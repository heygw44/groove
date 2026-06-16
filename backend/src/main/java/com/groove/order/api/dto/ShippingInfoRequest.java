package com.groove.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 주문 생성 요청의 배송지 블록 — 회원/게스트 공통 필수.
 * safePackagingRequested 는 누락 시 false.
 */
public record ShippingInfoRequest(
        @Schema(description = "수령인 이름", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 50)
        String recipientName,

        @Schema(description = "수령인 연락처", example = "01012345678", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 20)
        String recipientPhone,

        @Schema(description = "기본 주소", example = "서울특별시 강남구 테헤란로 123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 500)
        String address,

        @Schema(description = "상세 주소 (선택)", example = "4층 401호")
        @Size(max = 200)
        String addressDetail,

        @Schema(description = "우편번호", example = "06234", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 20)
        String zipCode,

        @Schema(description = "LP 안전 포장 요청 여부 (누락 시 false)", example = "true",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "false")
        boolean safePackagingRequested
) {
}
