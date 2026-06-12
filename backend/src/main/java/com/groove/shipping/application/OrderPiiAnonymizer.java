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
 * 배송완료 + 보존기간이 지난 주문/배송의 PII 를 익명화하는 단일 진입점 (#170 Part B).
 * OrderPiiAnonymizationScheduler 가 배치 건별로 호출.
 *
 * 독립 트랜잭션(REQUIRES_NEW): ShippingProvisioner 와 동일하게 주문마다 격리된 커밋 경계를 둬, 한 건 실패가 같은 주기 다른 건
 * 익명화를 롤백하지 않게 한다. 호출자(스케줄러)가 건별로 예외를 잡아 다음 주기에 재시도.
 *
 * 배송지 PII 는 주문 시점 스냅샷이 배송 행에도 복사돼 있어(같은 수령인/주소), 배송을 order 와 함께 로드(findWithOrderById)해
 * 양쪽을 한 트랜잭션에서 마스킹한다. 모듈 의존 방향(shipping → order)은 reconciliation 과 동일하게 유지(order 가 shipping 을
 * 알지 않음).
 *
 * 멱등: 이미 익명화된 배송이면(anonymized_at != null) no-op — 배치 대상 조회와 엔티티 가드가 이중으로 막아 재실행·경합에 안전.
 */
@Component
public class OrderPiiAnonymizer {

    private static final Logger log = LoggerFactory.getLogger(OrderPiiAnonymizer.class);

    /**
     * anonymizeOrder 가 익명화하는 비배송 종착 상태(#188) — PENDING 은 비종착이라 OrderStatus.isTerminal() 로는 안 잡히고
     * COMPLETED/DELIVERED 는 배송완료 배치 담당이라 명시 열거한다.
     * 배치 조회(OrderPiiAnonymizationScheduler)와 트랜잭션 내 재검증이 같은 집합을 쓰도록 단일 소스로 둔다.
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
     * 배송(+주문)의 PII 를 마스킹하고 anonymized_at 을 찍는다. 배송이 없거나 이미 익명화됐으면
     * 건너뛴다.
     *
     * @return 새로 익명화하면 true, 없거나 이미 익명화돼 건너뛰면 false
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
     * 배송이 생성되지 않은 종착 주문(#188 — 미결제 PENDING / PAYMENT_FAILED / 배송 생성 전 CANCELLED)의 PII 를 마스킹하고
     * anonymized_at 을 찍는다. 주문이 없거나 이미 익명화됐으면 건너뛴다.
     *
     * 대상에 CANCELLED 가 섞여 있는데, 환불(AdminOrderService.refund)로 PAID/PREPARING→CANCELLED 전이된 주문은 배송 행을
     * 가진다 — 그 배송은 DELIVERED 가 아니라 anonymizeForShipping 배치가 닿지 않으므로, 여기서 orderId 로 배송을 찾아 함께
     * 마스킹해 PII 누수를 막는다(Shipping.anonymizePii 도 멱등).
     *
     * @return 새로 익명화하면 true, 없거나 이미 익명화돼 건너뛰면 false
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
        // TOCTOU 가드: 배치 조회는 시점 스냅샷이라, 조회 후 결제 콜백 등으로 주문이 PAID 로 전진했을 수 있다. 트랜잭션
        // 안에서 상태를 재확인해 더 이상 비배송 종착 상태가 아니면(예: PAID) 익명화하지 않는다 — 마스킹된 배송지로 출고가
        // 막히는 것을 방지한다(ShippingProvisioner 의 isAnonymized 가드와 양방향 방어).
        if (!TERMINAL_NON_SHIPPING_STATUSES.contains(order.getStatus())) {
            log.info("PII 익명화 건너뜀: 비배송 종착 상태 아님 order={}, status={}", order.getOrderNumber(), order.getStatus());
            return false;
        }
        order.anonymizePii(now);
        // 환불로 취소된 주문만 배송 행을 가질 수 있다(PENDING/PAYMENT_FAILED 는 PAID 에 도달한 적이 없어 배송 미생성). 그
        // 배송은 비-DELIVERED 라 DELIVERED 배치가 닿지 않으므로 여기서 함께 마스킹한다 — CANCELLED 가 아니면 조회 불필요.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            shippingRepository.findByOrderId(orderId).ifPresent(shipping -> shipping.anonymizePii(now));
        }
        log.info("PII 익명화: order={} (비배송 종착 보존기간 경과)", order.getOrderNumber());
        return true;
    }
}
