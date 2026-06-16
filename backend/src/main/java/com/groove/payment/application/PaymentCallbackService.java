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
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * 웹훅·폴링 공통 결제 콜백 적용 트랜잭션 경계.
 * - PAID: Payment PENDING→PAID(paidAt 기록), Order PENDING→PAID, OrderPaidEvent 아웃박스 기록.
 * - FAILED: Payment PENDING→FAILED, Order PENDING→PAYMENT_FAILED, 재고 복원, 쿠폰 USED→ISSUED.
 * - 알 수 없는 거래/이미 종착 상태: 무시.
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

    public PaymentCallbackService(PaymentRepository paymentRepository, OutboxEventPublisher outboxEventPublisher,
                                  CouponApplicationService couponApplicationService,
                                  AlbumRepository albumRepository) {
        this.paymentRepository = paymentRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.couponApplicationService = couponApplicationService;
        this.albumRepository = albumRepository;
    }

    /** pgTransactionId 에 대한 IdempotencyService 키. */
    public static String idempotencyKeyFor(String pgTransactionId) {
        return "payment-callback:" + pgTransactionId;
    }

    /** PG 콜백 결과(PAID/FAILED)를 적용한다. failureReason 은 FAILED 일 때만 의미, null 이면 기본 사유 기록. */
    @Transactional
    public PaymentCallbackResult applyResult(String pgTransactionId, PaymentStatus result, String failureReason) {
        // 결과는 PAID 또는 FAILED 만 허용.
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
            // 후속 배송 생성 트리거를 아웃박스에 기록한다.
            outboxEventPublisher.publish(OrderPaidEvent.OUTBOX_AGGREGATE_TYPE, order.getId(), OrderPaidEvent.OUTBOX_EVENT_TYPE,
                    new OrderPaidEvent(order.getId(), order.getOrderNumber(), order.getMemberId(), payment.getId()));
            log.info("결제 확정(PAID): paymentId={}, order={}", payment.getId(), order.getOrderNumber());
        } else {
            payment.markFailed(failureReason != null ? failureReason : DEFAULT_FAILURE_REASON);
            order.changeStatus(OrderStatus.PAYMENT_FAILED, null);
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
