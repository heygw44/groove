package com.groove.payment.application;

import com.groove.common.idempotency.exception.IdempotencyConflictException;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.api.dto.TossWebhookRequest;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.gateway.GatewayQuery;
import com.groove.payment.gateway.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 토스페이먼츠 웹훅 처리(#296). 가상계좌 입금 등 confirm(#295) 시점에 즉시 확정되지 않은 PENDING 결제를
 * 사후 정산해 콜백 파이프({@link PaymentCallbackService#applyResult})에 합류시키는 것이 실질 가치다.
 *
 * 이벤트: PAYMENT_STATUS_CHANGED 만 처리(모든 결제수단 포괄, 가상계좌 입금 정산도 이 이벤트). 그 외 무시.
 * 재조회 검증: 토스 결제 웹훅엔 서명 헤더가 없어, 본문을 신뢰하지 않고 paymentKey 로 query 를 재호출한 권위 상태만 적용한다(위조 본문 무력화).
 * 로컬 선조회: outbound 전에 로컬 결제를 본다 — 미존재(위조·미연결)면 재조회 없이 무시(outbound 증폭 차단), 종착이면 무해 응답, PENDING 일 때만 재조회.
 * 멱등키 payment-callback:{paymentKey} 는 confirm·폴링과 공유 — 이미 처리/동시 처리여도 캐시 재생·비-PENDING·충돌 흡수로 중복 전이 없다.
 */
@Service
public class TossWebhookService {

    /** 결제 상태 변경 이벤트 — 모든 결제수단 공통. 그 외 이벤트는 무시한다. */
    static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";

    private static final Logger log = LoggerFactory.getLogger(TossWebhookService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentSettlementService settlementService;

    public TossWebhookService(PaymentRepository paymentRepository,
                              PaymentGateway paymentGateway,
                              PaymentSettlementService settlementService) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.settlementService = settlementService;
    }

    /**
     * 토스 웹훅을 처리한다. {@code PAYMENT_STATUS_CHANGED} 만 다루며, 로컬 PENDING 결제에 한해 재조회한 권위 상태가
     * PAID/FAILED 일 때 콜백 파이프에 적용한다. 무거운 처리는 정산 헬퍼에 위임하고 컨트롤러는 빠르게 200 으로 ACK 한다.
     */
    public PaymentCallbackResult handle(TossWebhookRequest body) {
        if (body == null) {
            // 본문 전체 누락(required=false) — 영구 malformed 이므로 거부 대신 무해 무시(토스 재전송 무의미).
            log.warn("토스 웹훅 무시: 본문 없음");
            return PaymentCallbackResult.ignored(null);
        }
        String eventType = body.eventType();
        if (!PAYMENT_STATUS_CHANGED.equals(eventType)) {
            log.info("토스 웹훅 무시: 대상 외 이벤트 eventType={}", eventType);
            return PaymentCallbackResult.ignored(null);
        }
        String paymentKey = body.data() == null ? null : body.data().paymentKey();
        if (paymentKey == null || paymentKey.isBlank()) {
            // 영구 malformed — 토스가 재전송해도 무의미하므로 거부(400)하지 않고 무해 무시(200)한다.
            log.warn("토스 웹훅 무시: data.paymentKey 누락");
            return PaymentCallbackResult.ignored(null);
        }

        // 로컬 선조회 — 미존재/종착이면 outbound 재조회 없이 무해 처리한다.
        Payment payment = paymentRepository.findByPgTransactionId(paymentKey).orElse(null);
        if (payment == null) {
            // 위조·미지 키, 또는 confirm 이 아직 실 paymentKey 를 연결하지 않은 시점 — 재조회 없이 무시(후속 정산이 처리).
            log.info("토스 웹훅 무시: 로컬 결제 없음 paymentKey={}", paymentKey);
            return PaymentCallbackResult.ignored(paymentKey);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("토스 웹훅 무시: 이미 종착 결제 paymentKey={}, status={}", paymentKey, payment.getStatus());
            return PaymentCallbackResult.alreadyProcessed(payment);
        }

        // 재조회 검증 — 본문 status 는 신뢰하지 않고 토스에 직접 조회한 권위 상태로 판정한다.
        GatewayQuery query = paymentGateway.query(paymentKey);
        PaymentStatus authoritative = query.status();
        if (authoritative != PaymentStatus.PAID && authoritative != PaymentStatus.FAILED) {
            if (authoritative == PaymentStatus.REFUNDED || authoritative == PaymentStatus.PARTIALLY_REFUNDED) {
                // 토스측 취소/환불 — 자동 정산 범위 외. 로컬 PENDING 과 불일치하므로 수동 확인이 필요하다.
                log.warn("토스 웹훅: 토스측 취소/환불 상태 — 자동 정산 범위 외, 수동 확인 필요 paymentKey={}, status={}",
                        paymentKey, authoritative);
            } else {
                // 아직 미확정(PENDING) — 추후 상태 변경 시 웹훅 재수신.
                log.info("토스 웹훅 무시: 비종착 재조회 상태 paymentKey={}, status={}", paymentKey, authoritative);
            }
            return PaymentCallbackResult.ignored(paymentKey);
        }

        // PAID 정산 전 금액 위변조 대조(#320) — 토스가 알려준 권위 정산금액이 저장 금액과 다르면 자동 전이하지 않고
        // 수동 확인 대상으로 남긴다(취소/환불 분기와 동일 패턴). 금액 미보고(null)면 검증을 생략한다.
        if (authoritative == PaymentStatus.PAID && query.settledAmountMismatches(payment.getAmount())) {
            log.warn("토스 웹훅: PAID 정산금액 불일치 — 자동 정산 보류, 수동 확인 필요 paymentKey={}, 저장={}, 토스={}",
                    paymentKey, payment.getAmount(), query.settledAmount());
            return PaymentCallbackResult.ignored(paymentKey);
        }

        String failureReason = authoritative == PaymentStatus.FAILED ? "토스 웹훅 결제 실패" : null;
        try {
            PaymentCallbackResult result = settlementService.settle(paymentKey, authoritative, failureReason);
            log.info("토스 웹훅 처리: paymentKey={} → {} ({})", paymentKey, authoritative, result.outcome());
            return result;
        } catch (IdempotencyConflictException conflict) {
            // confirm·폴링·다른 웹훅이 같은 키를 처리 중 — 그쪽이 전이를 완료하므로 무해 무시(토스 재전송 폭주 방지).
            log.info("토스 웹훅 무시: 동시 처리 중 paymentKey={}", paymentKey);
            return PaymentCallbackResult.ignored(paymentKey);
        }
    }
}
