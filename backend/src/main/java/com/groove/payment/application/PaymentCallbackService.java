package com.groove.payment.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderStatus;
import com.groove.order.event.OrderPaidEvent;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 결과 콜백 적용 트랜잭션 경계 (#W7-4) — 웹훅 수신({@code PaymentWebhookController} /
 * {@code PaymentWebhookHandler})과 폴링 동기화({@code PaymentReconciliationScheduler})의 공통 처리 경로.
 *
 * <h2>멱등 실행 규약</h2>
 * <p>{@link #applyResult} 는 {@link com.groove.common.idempotency.IdempotencyService#execute} 의
 * {@code action} 으로 호출되는 것을 전제로 자기 트랜잭션({@code @Transactional})을 관리하고 반환 전에 커밋한다 —
 * 호출자(컨트롤러/디스패처/스케줄러)는 비트랜잭션이어야 한다 ({@code IdempotencyService} 호출 규약). 이중
 * 안전선: PG 거래 식별자 단위 멱등성(IdempotencyService 같은 키 공유) + 결제 상태 재확인(PENDING 아니면 무시).
 *
 * <h2>처리</h2>
 * <ul>
 *   <li>PAID — Payment {@code PENDING→PAID}({@code paidAt} 기록), Order {@code PENDING→PAID},
 *       {@link OrderPaidEvent} 발행. 발행만 한다 — 후속 처리(배송 생성 등) 구독은 #W7-5.</li>
 *   <li>FAILED — 보상 트랜잭션: Payment {@code PENDING→FAILED}, Order {@code PENDING→PAYMENT_FAILED},
 *       각 OrderItem 의 album 재고 복원. 어느 단계든 실패하면 트랜잭션 전체가 롤백되어 다음 콜백/폴링에 재시도된다
 *       ({@code OrderService.cancel} 의 재고 복원과 같은 패턴 — Aggregate 조율은 도메인이 아닌 ApplicationService 책임).</li>
 *   <li>알 수 없는 거래 / 이미 종착 상태 — 무해하게 무시(상태 전이 0회).</li>
 * </ul>
 *
 * <p>주의: 결제 PENDING 중 주문이 다른 경로로 CANCELLED 된 경우(현재 {@code OrderService.cancel} 은 PENDING
 * 주문만 취소하므로 결제는 PENDING 으로 남는다) 콜백 적용 시 주문 상태 전이가 거부돼 트랜잭션이 롤백된다 — 이
 * "결제 중 취소" 정합성은 결제 취소/환불 흐름과 함께 별도 이슈에서 다룬다.
 */
@Service
public class PaymentCallbackService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackService.class);

    /** {@code failureReason} 미상 시 기록할 기본 사유. */
    private static final String DEFAULT_FAILURE_REASON = "PG 결제 실패 통보";

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentCallbackService(PaymentRepository paymentRepository, ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * {@code pgTransactionId} 에 대한 {@code IdempotencyService} 키. 웹훅(HTTP/인프로세스) 경로와 폴링 경로가
     * 같은 키를 써 서로의 중복까지 한 곳에서 차단한다 — 어느 조합으로 중복 수신해도 상태 전이는 1회다.
     */
    public static String idempotencyKeyFor(String pgTransactionId) {
        return "payment-callback:" + pgTransactionId;
    }

    /**
     * @param pgTransactionId PG 거래 식별자
     * @param result          PG 가 통보한 최종 결과 — {@link PaymentStatus#PAID} 또는 {@link PaymentStatus#FAILED}
     * @param failureReason   실패 사유 (FAILED 일 때만 의미, {@code null} 이면 기본 사유 기록)
     */
    @Transactional
    public PaymentCallbackResult applyResult(String pgTransactionId, PaymentStatus result, String failureReason) {
        // 방어선 — 호출 측이 이미 보장한다(웹훅 본문 검증/WebhookNotification 생성자가 클라이언트엔 400 으로,
        // 폴링은 query() 결과 가드로). 여기 걸리면 클라이언트 오류가 아니라 호출 코드 버그이므로 비즈니스 예외(4xx)
        // 가 아닌 IllegalArgumentException(→ 500) 이 맞다.
        if (result != PaymentStatus.PAID && result != PaymentStatus.FAILED) {
            throw new IllegalArgumentException("결제 콜백 결과는 PAID 또는 FAILED 여야 합니다: " + result);
        }
        Payment payment = paymentRepository.findWithOrderAndItemsByPgTransactionId(pgTransactionId).orElse(null);
        if (payment == null) {
            log.warn("결제 콜백: 알 수 없는 거래 pgTx={} — 무시", pgTransactionId);
            return PaymentCallbackResult.ignored(pgTransactionId);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("결제 콜백: 이미 처리됨 paymentId={}, status={} — 무시", payment.getId(), payment.getStatus());
            return PaymentCallbackResult.alreadyProcessed(payment);
        }

        Order order = payment.getOrder();
        if (result == PaymentStatus.PAID) {
            payment.markPaid();
            order.changeStatus(OrderStatus.PAID, null);
            eventPublisher.publishEvent(
                    new OrderPaidEvent(order.getId(), order.getOrderNumber(), order.getMemberId(), payment.getId()));
            log.info("결제 확정(PAID): paymentId={}, order={}", payment.getId(), order.getOrderNumber());
        } else {
            payment.markFailed(failureReason != null ? failureReason : DEFAULT_FAILURE_REASON);
            order.changeStatus(OrderStatus.PAYMENT_FAILED, null);
            int restored = 0;
            for (OrderItem item : order.getItems()) {
                item.getAlbum().adjustStock(item.getQuantity());
                restored++;
            }
            log.info("결제 실패(FAILED) 보상 트랜잭션: paymentId={}, order={}, 재고복원 {}건",
                    payment.getId(), order.getOrderNumber(), restored);
        }
        return PaymentCallbackResult.applied(payment);
    }
}
