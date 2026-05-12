package com.groove.order.api.dto;

import com.groove.order.domain.OrderShippingInfo;

/**
 * 주문 응답에 포함되는 배송지 블록 (API.md §3.5).
 *
 * <p>주문 본인(회원 GET / 게스트 lookup)만 보는 응답이므로 캡처된 배송지 전체를 그대로 echo 한다.
 * 운송장 번호·배송 상태는 별개의 {@code GET /shippings/{trackingNumber}} 응답에서 노출한다.
 */
public record OrderShippingResponse(
        String recipientName,
        String recipientPhone,
        String address,
        String addressDetail,
        String zipCode,
        boolean safePackagingRequested) {

    public static OrderShippingResponse from(OrderShippingInfo info) {
        return new OrderShippingResponse(
                info.recipientName(),
                info.recipientPhone(),
                info.address(),
                info.addressDetail(),
                info.zipCode(),
                info.safePackagingRequested());
    }
}
