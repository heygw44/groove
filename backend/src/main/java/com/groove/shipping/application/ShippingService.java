package com.groove.shipping.application;

import com.groove.order.domain.OrderStatus;
import com.groove.shipping.api.dto.ShippingResponse;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import com.groove.shipping.exception.ShippingNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 배송 조회 + 자동 진행 트랜잭션 경계.
 *
 * findByTrackingNumber: 운송장 번호로 단건 조회 — 없으면 ShippingNotFoundException(404).
 *
 * advanceToShipped/advanceToDelivered: 자동 진행 스케줄러가 식별자로 호출하는 상태 전이 단위 트랜잭션. 배송이 기대 상태일 때만
 * 전이하고 아니면 무해하게 무시한다.
 *
 * 주문 락스텝 연동: 배송이 전이되는 분기에서 같은 트랜잭션의 주문도 Order.advanceTo 로 한 단계 전진시킨다(합법 전이만).
 * 주문은 findWithOrderById 로 동반 로드해 N+1 을 피한다.
 *
 * 발송 전 취소·환불 동기화: 주문이 종착 상태면 자동 진행을 건너뛴다 — 종착 주문의 배송을 SHIPPED/DELIVERED 로 밀지 않는다.
 */
@Service
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);

    private final ShippingRepository shippingRepository;

    public ShippingService(ShippingRepository shippingRepository) {
        this.shippingRepository = shippingRepository;
    }

    @Transactional(readOnly = true)
    public ShippingResponse findByTrackingNumber(String trackingNumber) {
        Shipping shipping = shippingRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(ShippingNotFoundException::new);
        return ShippingResponse.from(shipping);
    }

    /**
     * 주문에 연결된 배송의 완료(DELIVERED) 시각을 조회한다.
     * 배송이 없거나 아직 DELIVERED 가 아니면 Optional.empty.
     */
    @Transactional(readOnly = true)
    public Optional<Instant> findDeliveredAt(Long orderId) {
        return shippingRepository.findByOrderId(orderId).map(Shipping::getDeliveredAt);
    }

    @Transactional
    public void advanceToShipped(Long shippingId) {
        advance(shippingId, ShippingStatus.PREPARING, OrderStatus.SHIPPED, Shipping::markShipped);
    }

    @Transactional
    public void advanceToDelivered(Long shippingId) {
        advance(shippingId, ShippingStatus.SHIPPED, OrderStatus.DELIVERED, Shipping::markDelivered);
    }

    /**
     * 주문에 연결된 배송을 취소(CANCELLED)시킨다.
     * 배송이 없거나 이미 종착(DELIVERED/CANCELLED)이면 무시한다.
     */
    @Transactional
    public void cancelForOrder(Long orderId) {
        shippingRepository.findByOrderId(orderId).ifPresent(shipping -> {
            if (!shipping.getStatus().isTerminal()) {
                shipping.cancel();
                log.info("발송 전 취소·환불로 배송 취소 — orderId={}, tracking={}", orderId, shipping.getTrackingNumber());
            }
        });
    }

    /**
     * 자동 진행 한 단계 — 배송이 기대 상태(from)이고 주문이 종착이 아닐 때만 배송을 전이(mark)시키고,
     * 같은 트랜잭션의 주문도 Order.advanceTo 로 락스텝 전진시킨다(합법 전이만). order 는 findWithOrderById 로 동반 로드된다.
     */
    private void advance(Long shippingId, ShippingStatus from, OrderStatus target, Consumer<Shipping> mark) {
        shippingRepository.findWithOrderById(shippingId).ifPresent(shipping -> {
            if (shipping.getStatus() == from && !shipping.getOrder().getStatus().isTerminal()) {
                mark.accept(shipping);
                shipping.getOrder().advanceTo(target);
            }
        });
    }
}
