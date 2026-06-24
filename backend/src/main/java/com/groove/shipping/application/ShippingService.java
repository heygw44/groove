package com.groove.shipping.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.shipping.api.dto.ShippingResponse;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import com.groove.shipping.exception.ShippingNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * 배송 조회 + 자동 진행 트랜잭션 경계.
 *
 * findByTrackingNumber: 운송장 번호로 단건 조회 — 없으면 ShippingNotFoundException(404).
 *
 * advanceToShipped/advanceToDelivered: 자동 진행 스케줄러가 식별자로 호출하는 상태 전이 단위 트랜잭션. 배송이 기대 상태일 때만
 * 전이하고 아니면 무해하게 무시한다.
 *
 * 주문 락스텝 연동: 배송이 전이되는 분기에서 같은 트랜잭션의 주문도 Order.advanceTo 로 한 단계 전진시킨다(합법 전이만).
 * 주문 행은 findByIdForUpdate 로 PESSIMISTIC_WRITE 잠그고 최신 상태를 재조회한다 — 같은 주문 행 락을 잡는 cancelPartially
 * (반품 부분취소)와 직렬화된다. 관리자 환불(RefundSteps)은 결제 행만 잠가 주문 락을 공유하진 않지만, 락 후 종착 재검증 +
 * 주문 행 쓰기 직렬화 덕에 이미 CANCELLED 된 주문을 SHIPPED/DELIVERED 로 되살리는 lost update 는 발생하지 않는다.
 *
 * 발송 전 취소·환불 동기화: 주문이 종착 상태면 자동 진행을 건너뛴다 — 종착 주문의 배송을 SHIPPED/DELIVERED 로 밀지 않는다.
 */
@Service
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);

    private final ShippingRepository shippingRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public ShippingService(ShippingRepository shippingRepository, OrderRepository orderRepository, Clock clock) {
        this.shippingRepository = shippingRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
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
     * 자동 진행 한 단계 — 배송이 기대 상태(from)이고 주문이 종착이 아닐 때, 주문을 Order.advanceTo 로 먼저 전진시키고
     * 합법 전이로 성공했을 때만 배송도 같은 트랜잭션에서 전이(mark)시킨다. 주문 전이가 불법이면(false) 배송도 전진시키지
     * 않아 둘이 항상 락스텝으로 함께 움직인다(주문 PREPARING·배송 SHIPPED 같은 비대칭 상태를 만들지 않는다).
     *
     * 주문 행은 findByIdForUpdate 로 잠그고 최신 상태를 재조회한 뒤 종착 여부를 재검증한다 — 같은 주문 행 락을 잡는
     * cancelPartially 와 직렬화되고, 종착 재검증으로 발송 전 취소·환불이 CANCELLED 로 만든 주문을 자동진행이 덮어쓰지 않는다.
     * 배송은 먼저 findById 로 프록시 주문만 들고 있다가 findByIdForUpdate 로 신선하게 하이드레이션해야
     * 1차 캐시가 stale 상태를 반환하는 함정을 피한다(여기서 order 프록시를 .getId() 외로 건드리면 안 된다).
     */
    private void advance(Long shippingId, ShippingStatus from, OrderStatus target, BiConsumer<Shipping, Instant> mark) {
        shippingRepository.findById(shippingId).ifPresent(shipping -> {
            if (shipping.getStatus() != from) {
                return;
            }
            Order order = orderRepository.findByIdForUpdate(shipping.getOrder().getId())
                    .orElseThrow(OrderNotFoundException::new);
            if (order.getStatus().isTerminal()) {
                return;
            }
            Instant now = clock.instant();
            // 주문 전이가 합법일 때만 배송을 전이 — 불법이면 배송도 그대로 둬 배송·주문 비대칭을 막는다.
            if (!order.advanceTo(target, now)) {
                return;
            }
            mark.accept(shipping, now);
        });
    }
}
