package com.groove.shipping.application;

import com.groove.order.domain.OrderStatus;
import com.groove.shipping.api.dto.ShippingResponse;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import com.groove.shipping.exception.ShippingNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송 조회 + 자동 진행 트랜잭션 경계.
 *
 * <p>{@link #findByTrackingNumber}: 운송장 번호로 단건 조회 — 없으면 {@link ShippingNotFoundException}(404).
 *
 * <p>{@link #advanceToShipped}/{@link #advanceToDelivered}: 자동 진행 스케줄러가 식별자로 호출하는 상태 전이
 * 단위 트랜잭션. 같은 주기·재실행에서 이미 전이됐을 수 있으므로(또는 동시 cancel 등) 현재 상태가 기대 상태일 때만
 * 전이하고 아니면 무해하게 무시한다 — 폴링 스케줄러는 한 건의 실패가 배치 전체를 막지 않도록 건별로 호출한다.
 *
 * <p><b>주문 락스텝 연동(이슈 #161)</b>: 배송이 실제로 전이되는 분기 안에서 같은 트랜잭션의 주문도
 * {@code Order#advanceTo} 로 한 단계 전진시킨다(합법 전이만, 아니면 무해 무시). 정상 흐름에서 배송이 DELIVERED 에
 * 도달하면 주문도 DELIVERED 가 되어 리뷰 작성 자격을 만족한다. 주문을 함께 로드하려고 {@code findWithOrderById}
 * 로 조회해 LAZY 추가 SELECT(배치 N+1)를 피한다.
 */
@Service
public class ShippingService {

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
                shipping.getOrder().advanceTo(OrderStatus.SHIPPED);
            }
        });
    }

    @Transactional
    public void advanceToDelivered(Long shippingId) {
        shippingRepository.findWithOrderById(shippingId).ifPresent(shipping -> {
            if (shipping.getStatus() == ShippingStatus.SHIPPED) {
                shipping.markDelivered();
                shipping.getOrder().advanceTo(OrderStatus.DELIVERED);
            }
        });
    }
}
