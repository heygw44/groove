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

/**
 * 배송완료 + 보존기간이 지난 주문/배송의 PII 를 익명화하는 단일 진입점 (#170 Part B) — {@link OrderPiiAnonymizationScheduler}
 * 가 배치 건별로 호출한다.
 *
 * <h2>독립 트랜잭션({@link Propagation#REQUIRES_NEW})</h2>
 * <p>{@link ShippingProvisioner} 와 동일하게 주문마다 격리된 커밋 경계를 둔다 — 한 건의 실패가 같은 주기의
 * 다른 건 익명화를 롤백하지 않는다. 호출자(스케줄러)가 건별로 예외를 잡아 다음 주기에 재시도한다.
 *
 * <h2>배송과 주문을 함께 마스킹</h2>
 * <p>배송지 PII 는 주문 시점 스냅샷이 배송 행에도 복사돼 있으므로(같은 수령인/주소), 배송을 {@code order}
 * 와 함께 로드({@link ShippingRepository#findWithOrderById})해 양쪽을 한 트랜잭션에서 마스킹한다.
 * 모듈 의존 방향(shipping → order)은 reconciliation 과 동일하게 유지된다 — order 가 shipping 을 알지 않는다.
 *
 * <h2>멱등</h2>
 * <p>이미 익명화된 배송이면({@code anonymized_at != null}) no-op 으로 끝낸다 — 배치 대상 조회와 엔티티 가드가
 * 이중으로 막아 재실행·경합에 안전하다.
 */
@Component
public class OrderPiiAnonymizer {

    private static final Logger log = LoggerFactory.getLogger(OrderPiiAnonymizer.class);

    private final ShippingRepository shippingRepository;
    private final OrderRepository orderRepository;

    public OrderPiiAnonymizer(ShippingRepository shippingRepository, OrderRepository orderRepository) {
        this.shippingRepository = shippingRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 배송(+주문)의 PII 를 마스킹하고 {@code anonymized_at} 을 찍는다. 배송이 없거나 이미 익명화됐으면 건너뛴다.
     *
     * @return 새로 익명화하면 {@code true}, 없거나 이미 익명화돼 건너뛰면 {@code false}
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
     * 배송이 생성되지 않은 종착 주문(#188 — 미결제 PENDING / PAYMENT_FAILED / 배송 생성 전 CANCELLED)의 PII 를
     * 마스킹하고 {@code anonymized_at} 을 찍는다. 주문이 없거나 이미 익명화됐으면 건너뛴다.
     *
     * <p>대상 집합에 CANCELLED 가 섞여 있는데, 환불({@code AdminOrderService.refund})로 PAID/PREPARING→CANCELLED
     * 전이된 주문은 배송 행을 가진다 — 그 배송은 DELIVERED 가 아니라 {@link #anonymizeForShipping} 배치가 닿지
     * 않으므로, 여기서 orderId 로 배송을 찾아 함께 마스킹해 PII 누수를 막는다({@link Shipping#anonymizePii} 도 멱등).
     *
     * @return 새로 익명화하면 {@code true}, 없거나 이미 익명화돼 건너뛰면 {@code false}
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
        order.anonymizePii(now);
        // 환불로 취소된 주문만 배송 행을 가질 수 있다(PENDING/PAYMENT_FAILED 는 PAID 에 도달한 적이 없어 배송 미생성).
        // 그 배송은 비-DELIVERED 라 DELIVERED 배치가 닿지 않으므로 여기서 함께 마스킹한다 — CANCELLED 가 아니면 조회 불필요.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            shippingRepository.findByOrderId(orderId).ifPresent(shipping -> shipping.anonymizePii(now));
        }
        log.info("PII 익명화: order={} (비배송 종착 보존기간 경과)", order.getOrderNumber());
        return true;
    }
}
