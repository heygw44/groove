package com.groove.claim.api.dto;

import com.groove.claim.domain.Claim;
import com.groove.claim.domain.ClaimStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 관리자 반품 목록 요약 응답 (#239).
 *
 * <p>목록은 항목을 펼치지 않아(N+1 회피) 항목 수량은 노출하지 않는다 — 상세는 {@code GET /admin/claims/{id}} 가
 * {@link ClaimResponse} 로 제공한다. {@code orderNumber} 는 목록 쿼리가 {@code order} 를 fetch 해 노출한다.
 *
 * @param claimId      반품 식별자
 * @param orderNumber  반품 대상 주문 번호
 * @param status       반품 상태
 * @param refundAmount 확정 환불액 — REFUNDED 전에는 0
 * @param createdAt    접수 시각
 */
public record AdminClaimSummaryResponse(
        @Schema(description = "반품 식별자", example = "5")
        Long claimId,
        @Schema(description = "반품 대상 주문 번호", example = "ORD-20260606-A1B2C3")
        String orderNumber,
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
                claim.getStatus(),
                claim.getRefundAmount(),
                claim.getCreatedAt());
    }
}
