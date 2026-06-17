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

import java.time.Clock;

/**
 * 결제 완료 주문에 배송을 생성(프로비저닝)하는 단일 진입점 — 아웃박스 컨슈머 OrderPaidOutboxHandler 와
 * 보충 스케줄러 ShippingReconciliationScheduler 가 같은 로직을 공유한다.
 *
 * 독립 트랜잭션(REQUIRES_NEW): 주문마다 격리된 커밋 경계를 둔다.
 *
 * 한 주문당 배송 1건: existsByOrderId 로 미리 거르고, 최종 방어선은 uk_shipping_order UNIQUE 다 — saveAndFlush 로 충돌을 즉시 드러낸다.
 *
 * 충돌(DataIntegrityViolationException)·일시 장애 예외는 호출자(컨슈머/스케줄러)로 전파한다.
 */
@Component
public class ShippingProvisioner {

    private static final Logger log = LoggerFactory.getLogger(ShippingProvisioner.class);

    private final ShippingRepository shippingRepository;
    private final OrderRepository orderRepository;
    private final TrackingNumberGenerator trackingNumberGenerator;
    private final Clock clock;

    public ShippingProvisioner(ShippingRepository shippingRepository,
                               OrderRepository orderRepository,
                               TrackingNumberGenerator trackingNumberGenerator,
                               Clock clock) {
        this.shippingRepository = shippingRepository;
        this.orderRepository = orderRepository;
        this.trackingNumberGenerator = trackingNumberGenerator;
        this.clock = clock;
    }

    /**
     * 주문에 배송이 없으면 PREPARING 배송을 만들고 운송장을 발급한 뒤, 주문을 PAID→PREPARING 으로 락스텝 전진시키고
     * 운송장 번호를 비정규화한다. 이미 있거나 주문이 없으면 false 를 반환하고 건너뛴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean provisionForOrder(Long orderId, String orderNumber) {
        if (shippingRepository.existsByOrderId(orderId)) {
            return false;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("배송 생성 건너뜀: 주문 없음 orderId={}", orderId);
            return false;
        }
        // 종착 주문(취소/환불)에는 배송을 만들지 않는다.
        if (order.getStatus().isTerminal()) {
            log.warn("배송 생성 건너뜀: 주문이 종착 상태({}) order={} — 발송 전 취소/환불", order.getStatus(), orderNumber);
            return false;
        }
        // 이미 PII 익명화된 주문에는 배송을 만들지 않는다(배송지가 마스킹돼 있음).
        if (order.isAnonymized()) {
            log.warn("배송 생성 건너뜀: 이미 PII 익명화된 주문 order={} — 배송지 마스킹됨", orderNumber);
            return false;
        }
        Shipping shipping = Shipping.prepare(order, order.getShippingInfo(), trackingNumberGenerator.generate());
        shippingRepository.saveAndFlush(shipping);
        // 운송장 번호를 주문에 비정규화한다(더티체킹으로 커밋 시 반영).
        order.recordTrackingNumber(shipping.getTrackingNumber());
        // 배송 생성(PREPARING)에 맞춰 주문도 PAID→PREPARING 으로 락스텝 전진시킨다(합법 전이만, 아니면 무해 무시).
        order.advanceTo(OrderStatus.PREPARING, clock.instant());
        log.info("배송 생성: order={}, tracking={}", orderNumber, shipping.getTrackingNumber());
        return true;
    }
}
