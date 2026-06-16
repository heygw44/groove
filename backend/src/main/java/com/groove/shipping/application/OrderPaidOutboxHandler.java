package com.groove.shipping.application;

import com.groove.common.outbox.OutboxEvent;
import com.groove.common.outbox.OutboxEventHandler;
import com.groove.order.event.OrderPaidEvent;
import com.groove.shipping.domain.ShippingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 결제 완료(OrderPaid) 아웃박스 컨슈머 (#237) — 인프로세스 {@code @TransactionalEventListener} 를 대체한다.
 *
 * <p>{@code OutboxRelayScheduler} 가 미발행 {@code ORDER_PAID} 이벤트를 디스패치하면, payload 를
 * {@link OrderPaidEvent} 로 역직렬화해 {@link ShippingProvisioner#provisionForOrder} 로 배송을 생성한다.
 *
 * <p><b>멱등</b>: provisionForOrder 는 {@code existsByOrderId} 선검사 + {@code uk_shipping_order} 방어선으로
 * 멱등하다. {@link DataIntegrityViolationException} 은 무조건 흡수하지 않는다 — saveAndFlush 는 운송장 중복
 * ({@code uk_shipping_tracking})·FK·NOT NULL 등 다른 제약도 위반할 수 있어, 그걸 "이미 처리됨"으로 삼키면 배송이
 * 안 만들어졌는데도 발행 완료로 표시돼 이벤트가 유실된다. 따라서 충돌 후 해당 주문 배송이 실제 존재할 때만(=중복
 * 이벤트/경합) 흡수하고, 그 외 위반은 전파해 다음 주기에 재시도시킨다(릴레이 at-least-once + 멱등 = 정확히 1회 효과).
 * 종착/익명화 주문 가드는 provisionForOrder 내부에 있다.
 */
@Component
public class OrderPaidOutboxHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidOutboxHandler.class);

    private final ShippingProvisioner provisioner;
    private final ShippingRepository shippingRepository;
    private final ObjectMapper objectMapper;

    public OrderPaidOutboxHandler(ShippingProvisioner provisioner, ShippingRepository shippingRepository,
                                  ObjectMapper objectMapper) {
        this.provisioner = provisioner;
        this.shippingRepository = shippingRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return OrderPaidEvent.OUTBOX_EVENT_TYPE;
    }

    @Override
    public void handle(OutboxEvent event) {
        OrderPaidEvent payload = objectMapper.readValue(event.getPayload(), OrderPaidEvent.class);
        try {
            provisioner.provisionForOrder(payload.orderId(), payload.orderNumber());
        } catch (DataIntegrityViolationException e) {
            // 충돌이 "이 주문 배송이 이미 있음"(중복 이벤트/경합)일 때만 흡수. 운송장 중복 등 다른 위반이면 배송이
            // 미생성이므로 전파해 재시도한다 — 미생성을 발행 완료로 오인해 유실하지 않기 위함.
            if (shippingRepository.existsByOrderId(payload.orderId())) {
                log.info("배송 생성 건너뜀: order={} 이미 존재(중복 이벤트/경합)", payload.orderNumber());
            } else {
                throw e;
            }
        }
    }
}
