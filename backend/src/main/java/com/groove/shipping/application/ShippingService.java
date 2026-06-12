package com.groove.shipping.application;

import com.groove.order.domain.Order;
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
 * 배송은 물리적 진행(스케줄러=택배사 시뮬레이션)을 반영해 항상 전진시키되, 주문이 종착 상태(환불 CANCELLED 등)라 배송을 더
 * 이상 따라가지 못하면 상태 발산을 WARN 으로 드러낸다 — 환불 시 배송 자체를 중단/취소하는 보강은 별도 과제(배송 상태 머신에
 * 취소 상태 부재).
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

    @Transactional
    public void advanceToShipped(Long shippingId) {
        shippingRepository.findWithOrderById(shippingId).ifPresent(shipping -> {
            if (shipping.getStatus() == ShippingStatus.PREPARING) {
                shipping.markShipped();
                lockstepOrder(shipping, OrderStatus.SHIPPED);
            }
        });
    }

    @Transactional
    public void advanceToDelivered(Long shippingId) {
        shippingRepository.findWithOrderById(shippingId).ifPresent(shipping -> {
            if (shipping.getStatus() == ShippingStatus.SHIPPED) {
                shipping.markDelivered();
                lockstepOrder(shipping, OrderStatus.DELIVERED);
            }
        });
    }

    /**
     * 배송 전이에 주문을 락스텝으로 따라가게 한다. Order.advanceTo 는 합법 전이만 수행하므로(아니면 무해 무시) 정상 흐름·관리자
     * 선전이(이미 도달)에는 영향이 없다. 다만 주문이 종착 상태(취소/결제실패)라 배송을 따라갈 수 없으면 — 배송은 계속 전진하되 —
     * 상태 발산을 로그로 드러낸다(조용히 어긋나지 않도록, CodeRabbit 리뷰 반영).
     */
    private void lockstepOrder(Shipping shipping, OrderStatus target) {
        Order order = shipping.getOrder();
        if (!order.advanceTo(target) && order.getStatus().isTerminal()) {
            log.warn("주문이 종착 상태({})라 배송을 따라가지 못함 — order={}, tracking={}, 배송 목표={}",
                    order.getStatus(), order.getOrderNumber(), shipping.getTrackingNumber(), target);
        }
    }
}
