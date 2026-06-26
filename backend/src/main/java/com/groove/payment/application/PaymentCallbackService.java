package com.groove.payment.application;

import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.StockRestorer;
import com.groove.common.outbox.OutboxEventPublisher;
import com.groove.coupon.application.CouponApplicationService;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderStatus;
import com.groove.order.event.OrderPaidEvent;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentAmountMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 결제 콜백 적용 트랜잭션 경계 — 웹훅·폴링(pgTransactionId 조회)과 토스 confirm(orderId 조회)이 공유한다.
 * - PAID: Payment PENDING→PAID(paidAt 기록), Order PENDING→PAID, OrderPaidEvent 아웃박스 기록.
 * - FAILED: Payment PENDING→FAILED, Order PENDING→PAYMENT_FAILED, 재고 복원, 쿠폰 USED→ISSUED.
 * - 알 수 없는 거래/이미 종착 상태: 무시.
 *
 * 진입점은 applyResult(웹훅/폴링/토스 만료 리퍼)·applyConfirmedPaid(토스 confirm 성공)·linkPendingPaymentKey(토스 비-PAID confirm)다.
 * PENDING 잠금·흡수는 lockPending, PAID/FAILED 적용 본문은 applyTo 로 일원화한다.
 */
@Service
public class PaymentCallbackService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackService.class);

    /** failureReason 미상 시 기록할 기본 사유. */
    private static final String DEFAULT_FAILURE_REASON = "PG 결제 실패 통보";

    private final PaymentRepository paymentRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final CouponApplicationService couponApplicationService;
    private final AlbumRepository albumRepository;
    private final Clock clock;

    public PaymentCallbackService(PaymentRepository paymentRepository, OutboxEventPublisher outboxEventPublisher,
                                  CouponApplicationService couponApplicationService,
                                  AlbumRepository albumRepository, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.couponApplicationService = couponApplicationService;
        this.albumRepository = albumRepository;
        this.clock = clock;
    }

    /** pgTransactionId(토스는 paymentKey) 에 대한 IdempotencyService 키 — confirm/웹훅/폴링이 공유한다. */
    public static String idempotencyKeyFor(String pgTransactionId) {
        return "payment-callback:" + pgTransactionId;
    }

    /** PG 콜백 결과(PAID/FAILED)를 적용한다. failureReason 은 FAILED 일 때만 의미, null 이면 기본 사유 기록. */
    @Transactional
    public PaymentCallbackResult applyResult(String pgTransactionId, PaymentStatus result, String failureReason) {
        if (result != PaymentStatus.PAID && result != PaymentStatus.FAILED) {
            throw new IllegalArgumentException("결제 콜백 결과는 PAID 또는 FAILED 여야 합니다: " + result);
        }
        // FOR UPDATE 로 콜백 적용을 직렬화 — 동시 콜백(웹훅/폴링)의 패자는 락 해제 후 종착 상태를 읽어
        // IllegalStateException 대신 alreadyProcessed 로 흡수된다. order/items 는 같은 트랜잭션에서 lazy 로딩.
        Locked locked = lockPending(paymentRepository.findByPgTransactionIdForUpdate(pgTransactionId),
                pgTransactionId, "결제 콜백");
        if (locked.terminal() != null) {
            return locked.terminal();
        }
        return applyTo(locked.payment(), result, failureReason);
    }

    /**
     * 토스 confirm 성공 적용 — orderId 로 PENDING Payment 를 잠그고(FOR UPDATE) 잠정 pgTransactionId/method 를 실제값으로
     * 교체한 뒤 PAID 를 적용한다. successUrl 새로고침/재호출에도 1회만 전이한다(이미 PAID 면 흡수). confirmedAmount 는
     * confirm 전 이미 검증됐지만 직접 호출 방어로 한 번 더 대조하고, confirmedMethod 가 null(Mock)이면 보정을 건너뛴다.
     */
    @Transactional
    public PaymentCallbackResult applyConfirmedPaid(long orderId, String paymentKey, long confirmedAmount,
                                                    PaymentMethod confirmedMethod) {
        Locked locked = lockPending(paymentRepository.findByOrderIdForUpdate(orderId),
                "order:" + orderId, "토스 confirm 적용");
        if (locked.terminal() != null) {
            return locked.terminal();
        }
        Payment payment = locked.payment();
        if (payment.getAmount() != confirmedAmount) {
            throw new PaymentAmountMismatchException(payment.getOrder().getOrderNumber(), payment.getAmount(), confirmedAmount);
        }
        linkAndCorrect(payment, paymentKey, confirmedMethod);
        return applyTo(payment, PaymentStatus.PAID, null);
    }

    /**
     * 토스 confirm 이 즉시 PAID 가 아닌(가상계좌 등) PENDING 을 반환했을 때, 잠정 pgTransactionId/method 를 실제값으로
     * 교체한다(상태 전이 없음). 이후 입금 PAID 웹훅/폴링이 paymentKey 로 같은 행을 찾아 정산할 수 있게 한다.
     * 없음/종착이면 no-op, confirmedMethod 가 null 이면 보정을 생략한다.
     */
    @Transactional
    public void linkPendingPaymentKey(long orderId, String paymentKey, PaymentMethod confirmedMethod) {
        Locked locked = lockPending(paymentRepository.findByOrderIdForUpdate(orderId),
                "order:" + orderId, "토스 미확정 키 연결");
        if (locked.payment() != null) {
            linkAndCorrect(locked.payment(), paymentKey, confirmedMethod);
        }
    }

    /**
     * PENDING 결제의 잠정 pgTransactionId 를 실제 paymentKey 로 교체하고(환불 경로가 paymentKey 를 읽음) 잠정 method 를
     * 실제 결제수단으로 보정한다(#307). confirmedMethod 가 null 이면 보정 생략. 토스 confirm 의 PAID·비-PAID 진입점이 공유한다.
     */
    private static void linkAndCorrect(Payment payment, String paymentKey, PaymentMethod confirmedMethod) {
        payment.linkPgTransaction(paymentKey);
        if (confirmedMethod != null) {
            payment.correctMethod(confirmedMethod);
        }
    }

    /**
     * 잠긴 조회 결과를 PENDING 이면 그대로(payment non-null), 없음/이미 종착이면 흡수 결과(terminal non-null)로 매핑한다.
     * 세 진입점(웹훅/폴링·confirm·fail)의 동일한 "락 + null/종착 가드" 전문을 일원화한다.
     */
    private Locked lockPending(Optional<Payment> found, String ignoredKey, String context) {
        Payment payment = found.orElse(null);
        if (payment == null) {
            log.warn("{}: 알 수 없는 거래/주문 {} — 무시", context, ignoredKey);
            return new Locked(null, PaymentCallbackResult.ignored(ignoredKey));
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("{}: 이미 처리됨 paymentId={}, status={} — 무시", context, payment.getId(), payment.getStatus());
            return new Locked(null, PaymentCallbackResult.alreadyProcessed(payment));
        }
        return new Locked(payment, null);
    }

    /** lockPending 결과 — 정확히 하나만 non-null. payment 가 있으면 PENDING(적용 진행), terminal 이 있으면 흡수. */
    private record Locked(Payment payment, PaymentCallbackResult terminal) {
    }

    /**
     * PENDING Payment 에 PAID/FAILED 결과를 적용하는 공통 본문(웹훅/폴링/토스 confirm 공유).
     * - PAID: markPaid + Order PAID + OrderPaidEvent 아웃박스(배송 트리거).
     * - FAILED: markFailed + Order PAYMENT_FAILED + 재고 복원 + 쿠폰 USED→ISSUED.
     */
    private PaymentCallbackResult applyTo(Payment payment, PaymentStatus result, String failureReason) {
        // 적용 분기는 PAID/FAILED 만 유효 — 진입점 가드를 거치지만 본문에서도 불변식을 강제한다.
        if (result != PaymentStatus.PAID && result != PaymentStatus.FAILED) {
            throw new IllegalArgumentException("적용 결과는 PAID 또는 FAILED 여야 합니다: " + result);
        }
        Instant now = clock.instant();
        Order order = payment.getOrder();
        if (result == PaymentStatus.PAID) {
            payment.markPaid(now);
            order.changeStatus(OrderStatus.PAID, null, now);
            // 후속 배송 생성 트리거를 아웃박스에 기록한다.
            outboxEventPublisher.publish(OrderPaidEvent.OUTBOX_AGGREGATE_TYPE, order.getId(), OrderPaidEvent.OUTBOX_EVENT_TYPE,
                    new OrderPaidEvent(order.getId(), order.getOrderNumber(), order.getMemberId(), payment.getId()));
            log.info("결제 확정(PAID): paymentId={}, order={}", payment.getId(), order.getOrderNumber());
        } else {
            payment.markFailed(failureReason != null ? failureReason : DEFAULT_FAILURE_REASON);
            order.changeStatus(OrderStatus.PAYMENT_FAILED, null, now);
            // 재고 복원 — 원자적 가산 UPDATE(albumId 오름차순).
            int restored = StockRestorer.restore(albumRepository, order.getItems().stream()
                    .collect(Collectors.groupingBy(item -> item.getAlbum().getId(), Collectors.summingInt(OrderItem::getQuantity))));
            // 적용 쿠폰 USED→ISSUED 복원(미적용 주문은 no-op).
            couponApplicationService.restoreForOrder(order.getId());
            log.info("결제 실패(FAILED) 보상 트랜잭션: paymentId={}, order={}, 재고복원 {}건",
                    payment.getId(), order.getOrderNumber(), restored);
        }
        return PaymentCallbackResult.applied(payment);
    }
}
