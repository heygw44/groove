package com.groove.admin.application;

import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.StockRestorer;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * 환불의 트랜잭션 단계 — PG 환불 호출을 트랜잭션 밖으로 분리하기 위한 협력 빈 (#237).
 *
 * <p>{@link AdminOrderService#refund} 가 비트랜잭션 오케스트레이터로서 {@code prepare(검증+잠금, tx)} →
 * PG {@code refund()}(트랜잭션 밖, 멱등 키) → {@code apply(상태 반영+보상, tx)} 순으로 이 빈을 호출한다.
 * 같은 빈 안에서 {@code @Transactional} 메서드를 자기호출하면 프록시를 우회하므로 두 단계를 별도 빈으로 분리했다.
 *
 * <p><b>이중 보상 방지</b>: 보상(재고/쿠폰 복원, 상태 전이)은 {@link #apply} 의 {@code PESSIMISTIC_WRITE} 락 +
 * {@code status == PAID} 가드 안에서, PAID→REFUNDED 전이에서만 일어난다. 동시 환불 두 건이 {@link #prepare} 를
 * 모두 통과해도 PG 는 {@code refundIdempotencyKey} 로 1회만 실제 환불하고, apply 는 락으로 직렬화돼 첫 건만 PAID 를
 * 보고 보상하며 둘째는 REFUNDED 를 보고 멱등 no-op 한다 — 보상은 정확히 1회. 기존 단일 트랜잭션의 비관적 락
 * 안전성을 유지하면서 PG 호출만 락 밖으로 뺀 것이다.
 *
 * <p><b>prepare/apply 윈도우</b>: PG 환불 성공 후 apply 가 실패하면(예: 그 사이 주문이 다른 경로로 전이) 트랜잭션이
 * 롤백되지만 PG 환불은 이미 일어났다 — 이는 기존 단일 트랜잭션 설계도 동일했던 속성으로(보상 중간 실패 후 재시도 시
 * PG 멱등 키로 dedup), 재시도하면 prepare(여전히 PAID)→PG(dedup)→apply 로 정상화된다. 새 정합성 결함은 없다.
 */
@Component
class RefundSteps {

    private static final Logger log = LoggerFactory.getLogger(RefundSteps.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CouponApplicationService couponApplicationService;
    private final ShippingService shippingService;
    private final AlbumRepository albumRepository;

    RefundSteps(OrderRepository orderRepository,
                PaymentRepository paymentRepository,
                CouponApplicationService couponApplicationService,
                ShippingService shippingService,
                AlbumRepository albumRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.couponApplicationService = couponApplicationService;
        this.shippingService = shippingService;
        this.albumRepository = albumRepository;
    }

    /**
     * 환불 가능성 검증 + Payment {@code PESSIMISTIC_WRITE} 잠금 (tx). PG 호출에 필요한 원시값만 추출 후 커밋(락 해제).
     * 이미 REFUNDED 면 부수효과 없이 멱등 응답({@code alreadyRefunded}), 비-PAID 면 409, CANCELLED 전이 불가면 409.
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
            // 배송 시작(SHIPPED) 이후 등 — 환불해도 주문을 취소 상태로 둘 수 없으므로 차단 (DoD: 합법 전이만 허용).
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.CANCELLED);
        }
        // 결정적 멱등 키(#72) — 같은 결제에 환불을 재시도해도 PG 가 첫 응답을 캐시 재사용한다.
        return RefundPrep.proceed(payment.getPgTransactionId(), payment.getAmount(), payment.refundIdempotencyKey());
    }

    /**
     * PG 환불 성공 후 상태 반영 + 보상 (tx). Payment 를 {@code PESSIMISTIC_WRITE} 로 재잠금하고
     * {@code status == PAID} 일 때만 markRefunded + Order CANCELLED + 발송 전 배송 CANCELLED(#233) + 재고 복원 +
     * 쿠폰 USED→ISSUED 복원을 수행한다. 동시 환불이 먼저 적용돼 이미 REFUNDED 면 보상 없이 멱등 no-op.
     */
    @Transactional
    RefundResult apply(String orderNumber, String reason, Instant refundedAt) {
        Order order = orderRepository.findWithAlbumsByOrderNumber(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
        Payment payment = paymentRepository.findByOrderIdForUpdate(order.getId())
                .orElseThrow(PaymentNotFoundException::new);

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            // 동시 환불 두 번째 — 첫 건이 락 안에서 이미 보상하고 REFUNDED 로 커밋했다. 보상 중복 없이 멱등 응답.
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
        order.changeStatus(OrderStatus.CANCELLED, reason);
        // 발송 전(PREPARING) 배송이 있으면 같은 트랜잭션에서 CANCELLED 로 동기화한다 (이슈 #233). 없거나 종착이면 no-op.
        shippingService.cancelForOrder(order.getId());
        // 재고 복원 — 원자적 가산 UPDATE(albumId 오름차순)로 lost-update·데드락을 없앤다 (#234).
        int restored = StockRestorer.restore(albumRepository, order.getItems().stream()
                .collect(Collectors.groupingBy(item -> item.getAlbum().getId(), Collectors.summingInt(OrderItem::getQuantity))));
        // 쿠폰 적용된 주문이면 USED→ISSUED 복원 (이슈 #91 DoD HIGH 리스크: cancel + refund 양 경로 복원). 미적용 주문은 no-op.
        couponApplicationService.restoreForOrder(order.getId());
        log.info("관리자 환불 완료: order={}, paymentId={}, amount={}, 재고복원 {}건",
                orderNumber, payment.getId(), payment.getAmount(), restored);
        return RefundResult.refunded(order, payment, refundedAt);
    }

    /**
     * prepare 결과 — 이미 환불됨 멱등 응답({@code alreadyRefundedResult} non-null) 또는 신규 환불 진행에 필요한
     * PG 호출 원시값. PG 호출은 prepare 트랜잭션(락) 밖에서 이뤄지므로 엔티티가 아닌 원시값만 담는다.
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
