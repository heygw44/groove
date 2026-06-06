package com.groove.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 주문 생성 요청의 배송지 블록 (API.md §3.5).
 *
 * <p>회원/게스트 공통으로 필수다 — 배송지 없이는 결제 완료 후 배송 행을 만들 수 없다. 컬럼 길이는
 * {@code shipping} 테이블(ERD §4.13) 및 {@code orders} 의 배송지 스냅샷 컬럼과 일치하며, 도메인
 * 레이어({@code OrderShippingInfo}) 에서도 한 번 더 검증된다.
 *
 * <p>{@code safePackagingRequested} 는 누락 시 false (LP 안전 포장 미요청).
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

        @Schema(description = "LP 안전 포장 요청 여부 (누락 시 false)", example = "true")
        boolean safePackagingRequested
) {
}
