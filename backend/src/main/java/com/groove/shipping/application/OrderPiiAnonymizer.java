package com.groove.shipping.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * 배송완료 + 보존기간이 지난 주문/배송의 PII 를 익명화하는 단일 진입점. OrderPiiAnonymizationScheduler 가 배치 건별로 호출.
 *
 * 독립 트랜잭션(REQUIRES_NEW): 주문마다 격리된 커밋 경계를 둬 한 건 실패가 같은 주기 다른 건을 롤백하지 않게 한다.
 *
 * 배송지 PII 는 주문 시점 스냅샷이 배송 행에도 복사돼 있어, 배송을 order 와 함께 로드(findWithOrderById)해 양쪽을 한 트랜잭션에서 마스킹한다.
 *
 * 멱등: 이미 익명화된 배송이면(anonymized_at != null) no-op.
 */
@Component
public class OrderPiiAnonymizer {

    private static final Logger log = LoggerFactory.getLogger(OrderPiiAnonymizer.class);

    /**
     * anonymizeOrder 가 익명화하는 비배송 종착 상태(PENDING / PAYMENT_FAILED / CANCELLED).
     * 배치 조회와 트랜잭션 내 재검증이 같은 집합을 쓰도록 단일 소스로 둔다.
     */
    public static final Set<OrderStatus> TERMINAL_NON_SHIPPING_STATUSES =
            Set.copyOf(EnumSet.of(OrderStatus.PENDING, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED));

    private final ShippingRepository shippingRepository;
    private final OrderRepository orderRepository;

    public OrderPiiAnonymizer(ShippingRepository shippingRepository, OrderRepository orderRepository) {
        this.shippingRepository = shippingRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 배송(+주문)의 PII 를 마스킹하고 anonymized_at 을 찍는다. 배송이 없거나 이미 익명화됐으면 false 를 반환하고 건너뛴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean anonymizeForShipping(Long shippingId, Instant now) {
        Shipping shipping = shippingRepository.findWithOrderById(shippingId).orElse(null);
        if (shipping == null) {
            log.warn("PII 익명화 건너뜀: 배송 없음 shippingId={}", shippingId);
            return false;
        }
        if (shipping.isAnonymized()) {
            return false;
        }
        shipping.anonymizePii(now);
        Order order = shipping.getOrder();
        order.anonymizePii(now);
        log.info("PII 익명화: order={} (배송완료 보존기간 경과)", order.getOrderNumber());
        return true;
    }

    /**
     * 배송이 생성되지 않은 종착 주문(PENDING / PAYMENT_FAILED / 배송 생성 전 CANCELLED)의 PII 를 마스킹하고 anonymized_at 을 찍는다.
     * 주문이 없거나 이미 익명화됐으면 false 를 반환하고 건너뛴다. CANCELLED 주문이 배송 행을 가지면 그 배송 PII 도 함께 마스킹한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean anonymizeOrder(Long orderId, Instant now) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("PII 익명화 건너뜀: 주문 없음 orderId={}", orderId);
            return false;
        }
        if (order.isAnonymized()) {
            return false;
        }
        // 트랜잭션 안에서 상태를 재확인해 더 이상 비배송 종착 상태가 아니면(예: PAID) 익명화하지 않는다.
        if (!TERMINAL_NON_SHIPPING_STATUSES.contains(order.getStatus())) {
            log.info("PII 익명화 건너뜀: 비배송 종착 상태 아님 order={}, status={}", order.getOrderNumber(), order.getStatus());
            return false;
        }
        order.anonymizePii(now);
        // 환불로 취소된 주문만 배송 행을 가질 수 있어 CANCELLED 일 때만 배송을 찾아 함께 마스킹한다.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            shippingRepository.findByOrderId(orderId).ifPresent(shipping -> shipping.anonymizePii(now));
        }
        log.info("PII 익명화: order={} (비배송 종착 보존기간 경과)", order.getOrderNumber());
        return true;
    }
}
