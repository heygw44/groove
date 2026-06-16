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
 * 결제 완료(OrderPaid) 아웃박스 컨슈머. ORDER_PAID 이벤트 payload 를 OrderPaidEvent 로 역직렬화해
 * ShippingProvisioner.provisionForOrder 로 배송을 생성한다.
 *
 * <p>DataIntegrityViolationException 발생 시, 해당 주문 배송이 실제 존재할 때만 흡수하고
 * 그 외 위반은 전파해 다음 주기에 재시도시킨다.
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
            // 이 주문 배송이 이미 있을 때만 흡수, 그 외 위반은 전파해 재시도.
            if (shippingRepository.existsByOrderId(payload.orderId())) {
                log.info("배송 생성 건너뜀: order={} 이미 존재(중복 이벤트/경합)", payload.orderNumber());
            } else {
                throw e;
            }
        }
    }
}
