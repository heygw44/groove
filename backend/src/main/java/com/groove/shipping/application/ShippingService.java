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
 * advanceToShipped/advanceToDelivered: 자동 진행 스케줄러가 식별자로 호출하는 상태 전이 단위 트랜잭션. 같은 주기·재실행에서
 * 이미 전이됐거나(또는 동시 cancel 등) 현재 상태가 기대 상태일 때만 전이하고 아니면 무해하게 무시한다 — 폴링 스케줄러는 한
 * 건의 실패가 배치 전체를 막지 않도록 건별로 호출한다.
 *
 * 주문 락스텝 연동(이슈 #161): 배송이 실제로 전이되는 분기 안에서 같은 트랜잭션의 주문도 Order.advanceTo 로 한 단계
 * 전진시킨다(합법 전이만, 아니면 무해 무시). 정상 흐름에서 배송이 DELIVERED 에 도달하면 주문도 DELIVERED 가 되어 리뷰 작성
 * 자격을 만족. 주문을 함께 로드하려고 findWithOrderById 로 조회해 LAZY 추가 SELECT(배치 N+1)를 피한다.
 *
 * 발송 전 취소·환불 동기화(이슈 #233): 주문이 종착 상태(환불 CANCELLED 등)면 자동 진행을 아예 건너뛴다 — 종착 주문의
 * 배송을 SHIPPED/DELIVERED 로 밀지 않는다. 발송 전 취소·환불은 cancelForOrder 가 배송도 CANCELLED 로 전이시키므로,
 * 환불된 주문이 어떤 경로로도 DELIVERED 로 찍히지 않는다.
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
     * 주문에 연결된 배송의 완료(DELIVERED) 시각을 조회한다 — 반품 기한(수령 후 N일) 산정의 anchor 다 (#239).
     * 반품(claim)은 배송완료 후에만 접수되므로 {@code ClaimService.request} 가 이 시각 + 반품 window 로 기한을 잰다.
     * claim → shipping 결합을 이 좁은 read 메서드 하나로 한정한다(admin 이 {@link #cancelForOrder} 로 크로스모듈
     * 호출하는 선례와 동일). 배송이 없거나 아직 DELIVERED 가 아니면 {@code Optional.empty} — 호출 측이 기한 산정
     * 불가로 처리한다.
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
     * 주문에 연결된 배송을 취소(CANCELLED)시킨다 — 발송 전(PREPARING/SHIPPED) 주문이 취소·환불될 때 보상 트랜잭션
     * (AdminOrderService.refund)이 같은 트랜잭션에서 호출한다 (#233). 배송이 없거나 이미 종착(DELIVERED/CANCELLED)이면
     * 무해하게 무시한다 — refund 는 order.canTransitionTo(CANCELLED) 가 참일 때만(SHIPPED 이후 불가) 도달하므로
     * 실제로는 PREPARING 배송이 대상이다.
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
     * 자동 진행 한 단계 — 배송이 기대 상태({@code from})이고 주문이 종착(취소/결제실패)이 아닐 때만 배송을 전이({@code mark})
     * 시키고, 같은 트랜잭션의 주문도 {@code Order.advanceTo} 로 락스텝 전진시킨다(이슈 #161). 종착 주문은 전진시키지 않는다
     * (#233) — 발송 전 취소·환불된 주문이 어떤 경로로도 DELIVERED 로 찍히지 않게 한다. {@code Order.advanceTo} 는 합법
     * 전이만 수행하므로(관리자 선전이로 이미 도달했거나 한 단계 뒤처져도 무해 무시) 정상 흐름엔 영향이 없다. order 는
     * findWithOrderById 로 동반 로드돼 추가 SELECT(N+1)가 없다.
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
