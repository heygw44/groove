package com.groove.admin.application;

import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.StockRestorer;
import com.groove.catalog.album.event.AlbumStockChangedEvent;
import com.groove.coupon.application.CouponApplicationService;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentNotFoundException;
import com.groove.payment.exception.PaymentNotRefundableException;
import com.groove.shipping.application.ShippingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 환불의 트랜잭션 단계 협력 빈. AdminOrderService.refund 가 prepare(검증+잠금, tx) →
 * PG refund()(트랜잭션 밖, 멱등 키) → apply(상태 반영+보상, tx) 순으로 호출한다.
 *
 * 보상(재고/쿠폰 복원, 상태 전이)은 apply 의 PESSIMISTIC_WRITE 락 + status == PAID 가드 안에서
 * PAID→REFUNDED 전이에서만 일어난다. PG 는 refundIdempotencyKey 로 1회만 환불하고, apply 는 락으로
 * 직렬화돼 둘째는 REFUNDED 를 보고 멱등 no-op 한다.
 */
@Component
class RefundSteps {

    private static final Logger log = LoggerFactory.getLogger(RefundSteps.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CouponApplicationService couponApplicationService;
    private final ShippingService shippingService;
    private final AlbumRepository albumRepository;
    private final ApplicationEventPublisher eventPublisher;

    RefundSteps(OrderRepository orderRepository,
                PaymentRepository paymentRepository,
                CouponApplicationService couponApplicationService,
                ShippingService shippingService,
                AlbumRepository albumRepository,
                ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.couponApplicationService = couponApplicationService;
        this.shippingService = shippingService;
        this.albumRepository = albumRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 환불 가능성 검증 + Payment PESSIMISTIC_WRITE 잠금 (tx). PG 호출에 필요한 원시값만 추출 후 커밋(락 해제).
     * 이미 REFUNDED 면 부수효과 없이 멱등 응답(alreadyRefunded), 비-PAID 면 409, CANCELLED 전이 불가면 409.
     */
    @Transactional
    RefundPrep prepare(String orderNumber) {
        Order order = orderRepository.findWithAlbumsByOrderNumber(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
        Payment payment = paymentRepository.findByOrderIdForUpdate(order.getId())
                .orElseThrow(PaymentNotFoundException::new);

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return RefundPrep.alreadyRefunded(RefundResult.alreadyRefunded(order, payment));
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new PaymentNotRefundableException(payment.getStatus());
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            // SHIPPED 이후 등 CANCELLED 로 전이할 수 없으면 차단.
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.CANCELLED);
        }
        // 결정적 멱등 키 — 같은 결제에 환불을 재시도해도 PG 가 첫 응답을 캐시 재사용한다.
        return RefundPrep.proceed(payment.getPgTransactionId(), payment.getAmount(), payment.refundIdempotencyKey());
    }

    /**
     * PG 환불 성공 후 상태 반영 + 보상 (tx). Payment 를 PESSIMISTIC_WRITE 로 재잠금하고
     * status == PAID 일 때만 markRefunded + Order CANCELLED + 발송 전 배송 CANCELLED + 재고 복원 +
     * 쿠폰 USED→ISSUED 복원을 수행한다. 이미 REFUNDED 면 보상 없이 멱등 no-op.
     */
    @Transactional
    RefundResult apply(String orderNumber, String reason, Instant refundedAt) {
        Order order = orderRepository.findWithAlbumsByOrderNumber(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
        Payment payment = paymentRepository.findByOrderIdForUpdate(order.getId())
                .orElseThrow(PaymentNotFoundException::new);

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            // 동시 환불 두 번째 — 첫 건이 이미 보상하고 REFUNDED 로 커밋했으므로 멱등 응답.
            log.info("관리자 환불 적용: 이미 환불됨 order={}, paymentId={} — 멱등 no-op", orderNumber, payment.getId());
            return RefundResult.alreadyRefunded(order, payment);
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new PaymentNotRefundableException(payment.getStatus());
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.CANCELLED);
        }

        payment.markRefunded();
        order.changeStatus(OrderStatus.CANCELLED, reason, refundedAt);
        // 발송 전(PREPARING) 배송이 있으면 같은 트랜잭션에서 CANCELLED 로 동기화한다. 없거나 종착이면 no-op.
        shippingService.cancelForOrder(order.getId());
        // 재고 복원 — 원자적 가산 UPDATE(albumId 오름차순).
        Map<Long, Integer> quantityByAlbumId = order.getItems().stream()
                .collect(Collectors.groupingBy(item -> item.getAlbum().getId(), Collectors.summingInt(OrderItem::getQuantity)));
        int restored = StockRestorer.restore(albumRepository, quantityByAlbumId);
        // 복원된 album 들의 조회 캐시(상세/랜딩) 무효화.
        if (!quantityByAlbumId.isEmpty()) {
            eventPublisher.publishEvent(new AlbumStockChangedEvent(quantityByAlbumId.keySet()));
        }
        // 쿠폰 적용된 주문이면 USED→ISSUED 복원. 미적용 주문은 no-op.
        couponApplicationService.restoreForOrder(order.getId());
        log.info("관리자 환불 완료: order={}, paymentId={}, amount={}, 재고복원 {}건",
                orderNumber, payment.getId(), payment.getAmount(), restored);
        return RefundResult.refunded(order, payment, refundedAt);
    }

    /**
     * prepare 결과 — 이미 환불됨 멱등 응답(alreadyRefundedResult non-null) 또는 신규 환불에 필요한 PG 호출 원시값.
     */
    record RefundPrep(RefundResult alreadyRefundedResult, String pgTransactionId, long amount, String refundIdempotencyKey) {

        static RefundPrep alreadyRefunded(RefundResult result) {
            return new RefundPrep(result, null, 0L, null);
        }

        static RefundPrep proceed(String pgTransactionId, long amount, String refundIdempotencyKey) {
            return new RefundPrep(null, pgTransactionId, amount, refundIdempotencyKey);
        }

        boolean isAlreadyRefunded() {
            return alreadyRefundedResult != null;
        }
    }
}
