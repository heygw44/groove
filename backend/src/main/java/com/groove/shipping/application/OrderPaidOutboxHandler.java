package com.groove.shipping.application;

import com.groove.common.outbox.OutboxEvent;
import com.groove.common.outbox.OutboxEventHandler;
import com.groove.order.event.OrderPaidEvent;
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
 * 멱등하다. 중복 이벤트/경합으로 인한 {@link DataIntegrityViolationException}(이미 배송 존재)은 "이미 처리됨"으로
 * 흡수해 정상 종료한다 — 릴레이가 발행 완료로 표시한다. 그 밖의 일시 실패는 예외로 전파해 다음 주기에 재시도시킨다
 * (릴레이 at-least-once + 이 멱등성 = 정확히 1회 효과). 종착/익명화 주문 가드는 provisionForOrder 내부에 있다.
 */
@Component
public class OrderPaidOutboxHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidOutboxHandler.class);

    private final ShippingProvisioner provisioner;
    private final ObjectMapper objectMapper;

    public OrderPaidOutboxHandler(ShippingProvisioner provisioner, ObjectMapper objectMapper) {
        this.provisioner = provisioner;
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
        } catch (DataIntegrityViolationException alreadyExists) {
            log.info("배송 생성 건너뜀: order={} 이미 존재(중복 이벤트/경합)", payload.orderNumber());
        }
    }
}
