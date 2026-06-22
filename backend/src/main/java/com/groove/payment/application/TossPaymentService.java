package com.groove.payment.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.payment.api.dto.PaymentApiResponse;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.api.dto.PaymentCreateRequest;
import com.groove.payment.api.dto.TossCheckoutResponse;
import com.groove.payment.application.PaymentRequestSteps.PaymentRequestPrep;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentAmountMismatchException;
import com.groove.payment.exception.PaymentGatewayException;
import com.groove.payment.exception.PaymentNotFoundException;
import com.groove.payment.gateway.ConfirmResponse;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.PaymentResponse;
import com.groove.payment.gateway.TossPaymentProperties;
import com.groove.payment.gateway.toss.TossPaymentGateway;
import com.groove.common.idempotency.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 토스페이먼츠 동기 confirm 승인 흐름 오케스트레이터(#295).
 *
 * <p>① checkout: 주문 검증 + payable 을 PENDING 으로 저장(잠정 pgTx=toss-pending:{orderNumber})하고 프론트에 clientKey·orderId·amount 응답.
 * ② confirm(successUrl): 금액 위변조 검증 → 게이트웨이 confirm → 성공 시 {@link PaymentCallbackService} 로 PAID 적용.
 * ③ fail(failUrl): 기존 보상 경로(재고·쿠폰 복원) 1회 적용.
 *
 * <p>토스는 confirm 모델이라 {@code PaymentGateway.request()} 가 {@code UnsupportedOperationException} 이므로
 * {@code PaymentService.requestPayment}(request 경로)와 분리한다. 외부 confirm 호출은 트랜잭션 밖에서 한다.
 */
@Service
public class TossPaymentService {

    private static final Logger log = LoggerFactory.getLogger(TossPaymentService.class);

    /**
     * 요청 단계의 잠정 pgTransactionId 접두사. confirm 전까지는 토스 paymentKey 를 알 수 없어 orderNumber 로 채우되,
     * 실제 paymentKey 와 값 공간이 겹치지 않도록 접두사를 둔다(uk_payment_pg_tx 우연 충돌·폴링 오조회 방지).
     */
    static final String PENDING_PG_TX_PREFIX = "toss-pending:";

    private final PaymentRequestSteps steps;
    private final PaymentGateway paymentGateway;
    private final PaymentCallbackService callbackService;
    private final IdempotencyService idempotencyService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectProvider<TossPaymentProperties> tossProperties;

    public TossPaymentService(PaymentRequestSteps steps,
                              PaymentGateway paymentGateway,
                              PaymentCallbackService callbackService,
                              IdempotencyService idempotencyService,
                              OrderRepository orderRepository,
                              PaymentRepository paymentRepository,
                              ObjectProvider<TossPaymentProperties> tossProperties) {
        this.steps = steps;
        this.paymentGateway = paymentGateway;
        this.callbackService = callbackService;
        this.idempotencyService = idempotencyService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.tossProperties = tossProperties;
    }

    /**
     * 결제 요청(checkout). 검증 + payable 을 PENDING 으로 서버 저장하고 결제위젯 초기화 값을 응답한다.
     * 이미 접수된 결제가 있으면 그 값으로 멱등 응답한다(주문 레벨 멱등은 {@code steps.prepare} 가 판정).
     */
    public TossCheckoutResponse checkout(Long callerMemberId, PaymentCreateRequest request) {
        PaymentRequestPrep prep = steps.prepare(callerMemberId, request);
        PaymentApiResponse persisted = prep.isExisting() ? prep.existingResponse() : persistPending(prep, request);
        return new TossCheckoutResponse(clientKey(), persisted.orderNumber(), persisted.amount());
    }

    /**
     * successUrl confirm 처리. 저장된 payable 과 리다이렉트 amount 일치를 confirm 호출 전에 검증해 위변조를 차단하고,
     * paymentKey 기준 멱등 래퍼로 confirm + PAID 적용을 1회만 수행한다(successUrl 새로고침/재호출 안전).
     */
    public PaymentCallbackResult confirm(String paymentKey, String orderId, long amount) {
        Order order = orderRepository.findByOrderNumber(orderId).orElseThrow(OrderNotFoundException::new);
        Payment pending = paymentRepository.findByOrderId(order.getId()).orElseThrow(PaymentNotFoundException::new);
        if (pending.getStatus() != PaymentStatus.PENDING) {
            // 이미 PAID(새로고침) 또는 FAILED — 재승인 없이 현재 상태를 멱등 반환한다.
            log.info("토스 confirm 멱등 흡수: order={}, status={}", orderId, pending.getStatus());
            return PaymentCallbackResult.alreadyProcessed(pending);
        }
        // 금액 위변조 검증 — 저장 payable != 리다이렉트 amount 면 confirm 호출 없이 거부.
        if (pending.getAmount() != amount) {
            log.warn("토스 confirm 금액 위변조 의심: order={}, 저장={}, 요청={}", orderId, pending.getAmount(), amount);
            throw new PaymentAmountMismatchException(orderId, pending.getAmount(), amount);
        }

        long orderPk = order.getId();
        return idempotencyService.execute(
                PaymentCallbackService.idempotencyKeyFor(paymentKey),
                PaymentCallbackResult.class,
                () -> doConfirm(orderPk, paymentKey, orderId, amount));
    }

    /**
     * failUrl 처리. 기존 보상 경로(재고·쿠폰 복원)를 주문 단위 멱등 키로 1회만 적용한다.
     */
    public PaymentCallbackResult fail(String orderId, String code, String message) {
        Order order = orderRepository.findByOrderNumber(orderId).orElseThrow(OrderNotFoundException::new);
        long orderPk = order.getId();
        String reason = buildFailureReason(code, message);
        return idempotencyService.execute(
                PaymentCallbackService.idempotencyKeyForFailure(orderId),
                PaymentCallbackResult.class,
                () -> callbackService.applyConfirmFailure(orderPk, reason));
    }

    /** 토스는 request() 게이트웨이 호출 없이 잠정 pgTx=toss-pending:{orderNumber} 로 PENDING Payment 를 저장한다. */
    private PaymentApiResponse persistPending(PaymentRequestPrep prep, PaymentCreateRequest request) {
        PaymentResponse synthetic = new PaymentResponse(
                PENDING_PG_TX_PREFIX + prep.orderNumber(), PaymentStatus.PENDING, TossPaymentGateway.PROVIDER);
        try {
            return steps.persist(prep, request.method(), synthetic);
        } catch (DataIntegrityViolationException duplicate) {
            // uk_payment_order 동시 충돌 — 기존 결제를 재조회해 멱등 응답한다.
            return steps.findExistingForOrder(prep.orderId()).orElseThrow(() -> duplicate);
        }
    }

    /**
     * confirm(외부 호출, tx 밖) → PAID 면 적용. 비-PAID(가상계좌 PENDING 등)는 잠정 pgTx 를 실제 paymentKey 로 교체해
     * 후속 웹훅/폴링이 정산할 수 있게 한 뒤, 멱등 캐시에 종착 결과를 남기지 않도록 예외로 빠져나간다(재호출 시 재처리 허용).
     */
    private PaymentCallbackResult doConfirm(long orderPk, String paymentKey, String orderId, long amount) {
        ConfirmResponse confirmed = paymentGateway.confirm(paymentKey, orderId, amount);
        if (confirmed.status() != PaymentStatus.PAID) {
            callbackService.linkPendingPaymentKey(orderPk, confirmed.pgTransactionId());
            log.info("토스 confirm 비-PAID 결과: order={}, status={} — paymentKey 연결 후 후속 정산 대기", orderId, confirmed.status());
            throw new PaymentGatewayException(
                    new IllegalStateException("토스 결제가 즉시 확정되지 않았습니다: " + confirmed.status()));
        }
        return callbackService.applyConfirmedPaid(orderPk, confirmed.pgTransactionId(), amount);
    }

    private String clientKey() {
        TossPaymentProperties props = tossProperties.getIfAvailable();
        return props != null ? props.clientKey() : null;
    }

    private static String buildFailureReason(String code, String message) {
        String c = (code == null || code.isBlank()) ? "UNKNOWN" : code;
        String m = (message == null) ? "" : message;
        return ("토스 결제 실패 [" + c + "] " + m).strip();
    }
}
