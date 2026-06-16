package com.groove.claim.api.dto;

import com.groove.claim.domain.Claim;
import com.groove.claim.domain.ClaimItem;
import com.groove.claim.domain.ClaimStatus;
import com.groove.claim.domain.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 반품 상세 응답 (#239) — 회원 조회·관리자 조회·승인/거부/환불 결과 공용.
 *
 * @param claimId         반품 식별자
 * @param orderNumber     반품 대상 주문 번호
 * @param claimType       클레임 종류 — CANCEL(부분 취소)/RETURN(반품)
 * @param status          반품 상태
 * @param reason          반품 사유
 * @param rejectionReason 거부 사유 — REJECTED 가 아니면 {@code null}
 * @param refundAmount    확정 환불액 — REFUNDED 전이 전에는 0
 * @param items           반품 항목
 * @param requestedAt     접수 시각 (= createdAt)
 * @param approvedAt      승인 시각 — 미승인이면 {@code null}
 * @param inTransitAt     회수 시작 시각 — {@code null} 가능
 * @param inspectingAt    검수 시작 시각 — {@code null} 가능
 * @param completedAt     종착(환불/거부) 시각 — {@code null} 가능
 */
public record ClaimResponse(
        @Schema(description = "반품 식별자", example = "5")
        Long claimId,
        @Schema(description = "반품 대상 주문 번호", example = "ORD-20260606-A1B2C3")
        String orderNumber,
        @Schema(description = "클레임 종류 — CANCEL(부분 취소)/RETURN(반품)", example = "RETURN")
        ClaimType claimType,
        @Schema(description = "반품 상태", example = "INSPECTING")
        ClaimStatus status,
        @Schema(description = "반품 사유", example = "단순 변심")
        String reason,
        @Schema(description = "거부 사유 — REJECTED 가 아니면 null", example = "사용 흔적 있음")
        String rejectionReason,
        @Schema(description = "확정 환불액 — REFUNDED 전에는 0", example = "13000")
        long refundAmount,
        @Schema(description = "반품 항목")
        List<Item> items,
        @Schema(description = "접수 시각 (ISO-8601 UTC)", example = "2026-06-10T09:00:00Z")
        Instant requestedAt,
        @Schema(description = "승인 시각 — 미승인이면 null")
        Instant approvedAt,
        @Schema(description = "회수 시작 시각 — null 가능")
        Instant inTransitAt,
        @Schema(description = "검수 시작 시각 — null 가능")
        Instant inspectingAt,
        @Schema(description = "종착(환불/거부) 시각 — null 가능")
        Instant completedAt
) {

    /**
     * 반품 항목 응답.
     *
     * @param orderItemId        주문 항목 식별자
     * @param albumTitleSnapshot 주문 시점 앨범 제목
     * @param quantity           반품 수량
     * @param unitPriceSnapshot  주문 시점 단가
     * @param gross              정가 합 (단가 × 수량, 할인 미반영)
     */
    public record Item(
            @Schema(description = "주문 항목 식별자", example = "10")
            Long orderItemId,
            @Schema(description = "주문 시점 앨범 제목", example = "OK Computer")
            String albumTitleSnapshot,
            @Schema(description = "반품 수량", example = "1")
            int quantity,
            @Schema(description = "주문 시점 단가", example = "15000")
            long unitPriceSnapshot,
            @Schema(description = "정가 합 (단가 × 수량)", example = "15000")
            long gross
    ) {

        static Item from(ClaimItem item) {
            return new Item(
                    item.getOrderItem().getId(),
                    item.getOrderItem().getAlbumTitleSnapshot(),
                    item.getQuantity(),
                    item.getUnitPriceSnapshot(),
                    item.getGross());
        }
    }

    public static ClaimResponse from(Claim claim) {
        List<Item> items = claim.getItems().stream().map(Item::from).toList();
        return new ClaimResponse(
                claim.getId(),
                claim.getOrder().getOrderNumber(),
                claim.getClaimType(),
                claim.getStatus(),
                claim.getReason(),
                claim.getRejectionReason(),
                claim.getRefundAmount(),
                items,
                claim.getCreatedAt(),
                claim.getApprovedAt(),
                claim.getInTransitAt(),
                claim.getInspectingAt(),
                claim.getCompletedAt());
    }
}
