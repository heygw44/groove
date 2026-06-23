package com.groove.payment.application;

import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.toss.TossPaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 결제 폴링 동기화 스케줄러 — min-age 이상 PENDING 으로 머문 결제를 PG query() 로 조회해 결과를 동기화한다.
 * 동기화는 PaymentCallbackService 를 동일 IdempotencyService 키로 호출한다. 건별로 격리하고 batch-size 로 처리량을 제한한다.
 * 주기/초기지연/최소경과시간/처리상한은 groove.payment.reconciliation.{interval,initial-delay,min-age,batch-size}.
 *
 * <p>generic PG 경로는 무상태(retryCount 미보유)라 PENDING 밖으로 전이시키지 않으면 매 주기 재선택된다. 영구
 * 해소 불가(예: 잔존 non-TOSS pgTx 를 토스로 조회 → 404/502)·영구 PENDING 은 max-age(생성 후 경과 기준) 초과 시
 * FAILED 로 종결해 무한 폴링을 끊는다(groove.payment.reconciliation.max-age). PG 가 취소 상태
 * (REFUNDED/PARTIALLY_REFUNDED)를 반환하면 미확정 결제이므로 즉시 FAILED 로 종결한다.
 */
@Component
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentSettlementService settlementService;
    private final Clock clock;
    private final Duration minAge;
    private final Duration tossPendingTimeout;
    private final Duration maxAge;
    private final Limit batchLimit;

    public PaymentReconciliationScheduler(PaymentRepository paymentRepository,
                                          PaymentGateway paymentGateway,
                                          PaymentSettlementService settlementService,
                                          Clock clock,
                                          @Value("${groove.payment.reconciliation.min-age:PT1M}") Duration minAge,
                                          @Value("${groove.payment.reconciliation.toss-pending-timeout:PT20M}") Duration tossPendingTimeout,
                                          @Value("${groove.payment.reconciliation.max-age:PT1H}") Duration maxAge,
                                          @Value("${groove.payment.reconciliation.batch-size:200}") int batchSize) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.settlementService = settlementService;
        this.clock = clock;
        this.minAge = Objects.requireNonNull(minAge, "minAge");
        this.tossPendingTimeout = Objects.requireNonNull(tossPendingTimeout, "tossPendingTimeout");
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
        // 음수 기간은 cutoff(now - 기간)를 미래로 밀어 조용히 오작동시킨다 — 부팅 시 차단한다. min-age/timeout 0 은 허용.
        if (minAge.isNegative()) {
            throw new IllegalArgumentException("groove.payment.reconciliation.min-age 는 음수일 수 없습니다: " + minAge);
        }
        if (tossPendingTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "groove.payment.reconciliation.toss-pending-timeout 는 음수일 수 없습니다: " + tossPendingTimeout);
        }
        // max-age 가 min-age 이하이면 폴링 대상(created_at < now-min-age)이 첫 주기에 곧바로 expired 로 판정돼
        // 모든 PENDING 이 query 결과와 무관하게 강제 FAILED 종결된다 — 오설정을 부팅 시 차단한다(maxAge 양수도 보장).
        if (maxAge.compareTo(minAge) <= 0) {
            throw new IllegalArgumentException(
                    "groove.payment.reconciliation.max-age 는 min-age 보다 커야 합니다: max-age=" + maxAge + ", min-age=" + minAge);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("groove.payment.reconciliation.batch-size 는 양수여야 합니다: " + batchSize);
        }
        this.batchLimit = Limit.of(batchSize);
    }

    @Scheduled(
            fixedDelayString = "${groove.payment.reconciliation.interval:PT1M}",
            initialDelayString = "${groove.payment.reconciliation.initial-delay:PT1M}")
    public void reconcilePendingPayments() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(minAge);
        Instant tossCutoff = now.minus(tossPendingTimeout);
        Instant giveUpCutoff = now.minus(maxAge);
        List<Payment> stale = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, cutoff, batchLimit);
        if (stale.isEmpty()) {
            return;
        }
        log.debug("결제 폴링 동기화 대상 {}건 (cutoff={}, limit={})", stale.size(), cutoff, batchLimit.max());
        for (Payment payment : stale) {
            // 토스(동기 confirm 모델)는 query 폴링 대상이 아니다 — query 는 무의미하다(매 주기 404 유발). 대신 미확정
            // PENDING(toss-pending: 잠정 pgTx)이 만료되면 FAILED + 보상으로 정리한다. 이게 보상의 '신뢰 가능한 경로'이며,
            // 공개 미인증 GET 인 failUrl 콜백은 상태를 바꾸지 않는다(교차 주문 조작 차단, #295 리뷰).
            if (TossPaymentGateway.PROVIDER.equals(payment.getPgProvider())) {
                if (payment.getPgTransactionId().startsWith(TossPaymentService.PENDING_PG_TX_PREFIX)
                        && payment.getCreatedAt().isBefore(tossCutoff)) {
                    reapAbandonedToss(payment.getPgTransactionId());
                }
                continue;
            }
            reconcileOne(payment, giveUpCutoff);
        }
    }

    /** 만료된 토스 미확정 PENDING 을 FAILED 로 확정하고 재고·쿠폰을 복원한다(공유 정산 헬퍼 재사용, 멱등). */
    private void reapAbandonedToss(String pgTransactionId) {
        try {
            settlementService.settle(pgTransactionId, PaymentStatus.FAILED, "토스 결제 미확정 만료 — 자동 실패 처리");
            log.info("토스 미확정 결제 만료 처리(FAILED+보상): pgTx={}", pgTransactionId);
        } catch (RuntimeException e) {
            log.warn("토스 미확정 결제 만료 처리 실패: pgTx={} — 다음 주기 재시도", pgTransactionId, e);
        }
    }

    /**
     * generic PG 의 PENDING 결제 한 건을 query 로 동기화한다. expired(생성 후 maxAge 초과)는 무한 폴링을 끊기 위한
     * 종결 게이트다.
     * <ul>
     *   <li>PAID/FAILED → 공유 정산 헬퍼로 동기화(기존 동작).</li>
     *   <li>REFUNDED/PARTIALLY_REFUNDED → PG 측 취소. 미확정(미캡처) 결제이므로 환불이 아니라 FAILED 로 종결(나이 무관).</li>
     *   <li>query 예외 + expired → 영구 해소 불가(예: 잔존 pgTx 404/502). FAILED 로 종결해 폴링 중단.</li>
     *   <li>query 가 PENDING(진행 중) 응답 → 나이와 무관하게 종결하지 않고 다음 주기 재시도. PG 가 정상 응답하는 한
     *       자동 실패시키지 않는다(느린 비동기 PG 의 정상 결제 오탐 방지 — #299 리뷰 #2).</li>
     *   <li>query 예외 + 만료 전 → 다음 주기 재시도.</li>
     * </ul>
     * <p>query 호출만 종결 게이트(catch)로 감싼다 — settle 실패가 query 실패로 오분류돼 PAID 확정 결제를 FAILED 로
     * 뒤집는 것을 막는다. settle 실패는 {@link #settleQuietly} 가 강제 종결 없이 다음 주기 재시도로 흡수한다.
     */
    private void reconcileOne(Payment payment, Instant giveUpCutoff) {
        String pgTransactionId = payment.getPgTransactionId();

        PaymentStatus pgStatus;
        try {
            pgStatus = paymentGateway.query(pgTransactionId);
        } catch (RuntimeException queryError) {
            // query 가 응답하지 못하는 영구 해소 불가(예: 잔존 pgTx 404/502)만 max-age 초과 시 종결한다.
            // settle 실패는 여기 섞이지 않는다(별도 catch).
            if (payment.getCreatedAt().isBefore(giveUpCutoff)) {
                terminateUnresolvable(pgTransactionId, queryError);
            } else {
                log.warn("결제 폴링 조회 실패: pgTx={} — 다음 주기에 재시도", pgTransactionId, queryError);
            }
            return;
        }

        if (pgStatus == PaymentStatus.PAID || pgStatus == PaymentStatus.FAILED) {
            PaymentCallbackResult result = settleQuietly(pgTransactionId, pgStatus, null);
            if (result != null) {
                log.info("결제 폴링 동기화: pgTx={} → {} ({})", pgTransactionId, pgStatus, result.outcome());
            }
            return;
        }
        if (pgStatus == PaymentStatus.REFUNDED || pgStatus == PaymentStatus.PARTIALLY_REFUNDED) {
            // PG 측 취소(CANCELED/PARTIAL_CANCELED). PENDING→REFUNDED 는 불법 전이이고 미캡처 결제 취소는
            // 곧 미성사이므로 FAILED + 보상으로 종결한다(DB/PG 불일치 해소).
            PaymentCallbackResult result = settleQuietly(
                    pgTransactionId, PaymentStatus.FAILED, "PG 측 결제 취소 확인 — 미확정 결제 실패 처리");
            if (result != null) {
                log.info("결제 폴링: PG 취소 확인 pgTx={}, pgStatus={} → FAILED 종결 ({})",
                        pgTransactionId, pgStatus, result.outcome());
            }
            return;
        }
        // 여기 도달 = PENDING(아직 미확정). PG 가 '진행 중'이라 답한 상태이므로 강제 종결하지 않고 다음 주기로 둔다.
        // max-age 종결은 query 가 응답하지 못하는 경우에만 적용한다(위 catch) — 느린 비동기 PG 의 정상 결제 오탐 방지(#299 리뷰 #2).
    }

    /**
     * PG 종착 상태 정산을 시도한다. settle 실패는 강제 종결 없이 흡수하고(null 반환 + WARN) 다음 주기에 재시도한다.
     * query 실패와 분리해 PAID 확정 결제가 settle 일시 오류로 FAILED 로 뒤집히는 것을 막는다(#299 리뷰).
     */
    private PaymentCallbackResult settleQuietly(String pgTransactionId, PaymentStatus terminalStatus, String failureReason) {
        try {
            return settlementService.settle(pgTransactionId, terminalStatus, failureReason);
        } catch (RuntimeException settleError) {
            log.warn("결제 폴링 정산 실패: pgTx={}, status={} — 다음 주기에 재시도", pgTransactionId, terminalStatus, settleError);
            return null;
        }
    }

    /** max-age 를 넘겨도 query 가 계속 실패하는 영구 해소 불가 결제를 FAILED 로 종결해 무한 폴링·WARN 을 끊는다(best-effort). */
    private void terminateUnresolvable(String pgTransactionId, RuntimeException queryError) {
        PaymentCallbackResult result = settleQuietly(
                pgTransactionId, PaymentStatus.FAILED, "결제 폴링 조회 반복 실패(max-age 초과) — 자동 실패 처리");
        if (result != null) {
            log.error("결제 폴링: max-age 초과 조회 반복 실패 pgTx={} → FAILED 종결(폴링 중단)", pgTransactionId, queryError);
        }
    }
}
