package com.groove.claim.api.dto;

import com.groove.claim.domain.Claim;
import com.groove.claim.domain.ClaimStatus;
import com.groove.claim.domain.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 관리자 반품 목록 요약 응답. 항목은 펼치지 않는다.
 */
public record AdminClaimSummaryResponse(
        @Schema(description = "반품 식별자", example = "5")
        Long claimId,
        @Schema(description = "반품 대상 주문 번호", example = "ORD-20260606-A1B2C3")
        String orderNumber,
        @Schema(description = "클레임 종류 — CANCEL(부분 취소)/RETURN(반품)", example = "RETURN")
        ClaimType claimType,
        @Schema(description = "반품 상태", example = "REQUESTED")
        ClaimStatus status,
        @Schema(description = "확정 환불액 — REFUNDED 전에는 0", example = "0")
        long refundAmount,
        @Schema(description = "접수 시각 (ISO-8601 UTC)", example = "2026-06-10T09:00:00Z")
        Instant createdAt
) {

    public static AdminClaimSummaryResponse from(Claim claim) {
        return new AdminClaimSummaryResponse(
                claim.getId(),
                claim.getOrder().getOrderNumber(),
                claim.getClaimType(),
                claim.getStatus(),
                claim.getRefundAmount(),
                claim.getCreatedAt());
    }
}
