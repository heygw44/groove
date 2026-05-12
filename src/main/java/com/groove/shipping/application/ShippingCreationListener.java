package com.groove.shipping.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.event.OrderPaidEvent;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 완료 시 배송을 자동 생성하는 리스너 (#W7-6) — {@link OrderPaidEvent} 를 {@link TransactionPhase#AFTER_COMMIT}
 * 으로 받아 {@link Shipping} 을 {@code PREPARING} 상태로 만들고 운송장 번호를 발급한다.
 *
 * <h2>{@code AFTER_COMMIT} + {@code REQUIRES_NEW} 인 이유</h2>
 * <p>이벤트는 결제 결과 적용 트랜잭션 안에서 발행된다(={@code PaymentCallbackService.applyResult()}). AFTER_COMMIT
 * 으로 바인딩하면 그 트랜잭션이 커밋된 뒤에만 실행되므로 "확정되지 않은 결제"에 대해 배송이 새지 않는다. 단 AFTER_COMMIT
 * 시점에는 호출 트랜잭션이 이미 종료돼 활성 트랜잭션이 없으므로, DB 쓰기를 하려면 자체 트랜잭션이 필요하다 —
 * {@link Propagation#REQUIRES_NEW}.
 *
 * <h2>한 주문당 배송 1건</h2>
 * <p>웹훅/폴링이 같은 결제 결과를 중복 전달해도 {@code PaymentCallbackService} 의 멱등 처리로 PAID 전이는 1회뿐이라
 * 이벤트도 1회 발행되는 것이 정상이지만, 방어적으로 {@link ShippingRepository#existsByOrderId} 로 한 번 거르고,
 * 최종 방어선은 {@code uk_shipping_order} UNIQUE 다 — {@code saveAndFlush} 로 동기적으로 flush 해 충돌을
 * {@link DataIntegrityViolationException} 으로 잡아 "이미 생성됨"으로 흡수한다.
 *
 * <h2>실패 격리</h2>
 * <p>AFTER_COMMIT 리스너의 예외는 트랜잭션 동기화 과정에서 흡수돼 호출자(결제 콜백)로 전파되지 않지만, 그렇다고 조용히
 * 삼키면 안 된다 — 배송 생성 실패는 여기서 로그로 남긴다(주문은 PAID 인데 배송이 없는 상태가 되며, 운영 보강 대상).
 */
@Component
public class ShippingCreationListener {

    private static final Logger log = LoggerFactory.getLogger(ShippingCreationListener.class);

    private final ShippingRepository shippingRepository;
    private final OrderRepository orderRepository;
    private final TrackingNumberGenerator trackingNumberGenerator;

    public ShippingCreationListener(ShippingRepository shippingRepository,
                                    OrderRepository orderRepository,
                                    TrackingNumberGenerator trackingNumberGenerator) {
        this.shippingRepository = shippingRepository;
        this.orderRepository = orderRepository;
        this.trackingNumberGenerator = trackingNumberGenerator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            if (shippingRepository.existsByOrderId(event.orderId())) {
                return;
            }
            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) {
                log.warn("배송 생성 건너뜀: 주문 없음 orderId={}", event.orderId());
                return;
            }
            Shipping shipping = Shipping.prepare(order, order.getShippingInfo(), trackingNumberGenerator.generate());
            shippingRepository.saveAndFlush(shipping);
            log.info("배송 생성: order={}, tracking={}", order.getOrderNumber(), shipping.getTrackingNumber());
        } catch (DataIntegrityViolationException e) {
            log.info("배송 생성 건너뜀: order={} 이미 존재(중복 이벤트/경합)", event.orderNumber());
        } catch (RuntimeException e) {
            log.error("배송 생성 실패: order={} — 주문은 PAID 이나 배송 미생성 (운영 보강 필요)", event.orderNumber(), e);
        }
    }
}
