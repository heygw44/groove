package com.groove.shipping.application;

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
        shippingRepository.findById(shippingId).ifPresent(shipping -> {
            if (shipping.getStatus() == ShippingStatus.PREPARING) {
                shipping.markShipped();
            }
        });
    }

    @Transactional
    public void advanceToDelivered(Long shippingId) {
        shippingRepository.findById(shippingId).ifPresent(shipping -> {
            if (shipping.getStatus() == ShippingStatus.SHIPPED) {
                shipping.markDelivered();
            }
        });
    }
}
