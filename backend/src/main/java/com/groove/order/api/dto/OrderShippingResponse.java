package com.groove.order.api.dto;

import com.groove.order.domain.OrderShippingInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주문 응답에 포함되는 배송지 블록 — 캡처된 배송지 전체를 그대로 echo 한다.
 */
public record OrderShippingResponse(
        @Schema(description = "수령인 이름", example = "홍길동")
        String recipientName,
        @Schema(description = "수령인 연락처", example = "01012345678")
        String recipientPhone,
        @Schema(description = "기본 주소", example = "서울특별시 강남구 테헤란로 123")
        String address,
        @Schema(description = "상세 주소", example = "4층 401호")
        String addressDetail,
        @Schema(description = "우편번호", example = "06234")
        String zipCode,
        @Schema(description = "LP 안전 포장 요청 여부", example = "true")
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
