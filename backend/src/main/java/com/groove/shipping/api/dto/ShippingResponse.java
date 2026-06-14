package com.groove.shipping.api.dto;

import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 배송 조회 응답 (API.md §3.7 — ShippingResponse).
 *
 * <p>공개 엔드포인트({@code GET /shippings/{trackingNumber}}) 응답이므로 수령인 이름·기본 주소·안전 포장 여부와
 * 진행 상태/시각만 노출하고, 연락처·우편번호·상세 주소는 내리지 않는다.
 *
 * @param trackingNumber          운송장 번호
 * @param status                  배송 상태 (PREPARING / SHIPPED / DELIVERED / CANCELLED)
 * @param recipientName           수령인 이름
 * @param address                 기본 주소
 * @param safePackagingRequested  LP 안전 포장 요청 여부
 * @param shippedAt               발송 시각 — PREPARING 동안 {@code null}
 * @param deliveredAt             배송 완료 시각 — DELIVERED 전까지 {@code null}
 * @param createdAt               배송 생성 시각
 */
public record ShippingResponse(
        @Schema(description = "운송장 번호 (UUID 형식)", example = "550e8400-e29b-41d4-a716-446655440000")
        String trackingNumber,
        @Schema(description = "배송 상태 (PREPARING / SHIPPED / DELIVERED / CANCELLED)", example = "SHIPPED")
        ShippingStatus status,
        @Schema(description = "수령인 이름", example = "홍길동")
        String recipientName,
        @Schema(description = "기본 주소", example = "서울특별시 강남구 테헤란로 123")
        String address,
        @Schema(description = "LP 안전 포장 요청 여부", example = "true")
        boolean safePackagingRequested,
        @Schema(description = "발송 시각 — PREPARING 동안 null")
        Instant shippedAt,
        @Schema(description = "배송 완료 시각 — DELIVERED 전까지 null")
        Instant deliveredAt,
        @Schema(description = "배송 생성 시각")
        Instant createdAt) {

    public static ShippingResponse from(Shipping shipping) {
        return new ShippingResponse(
                shipping.getTrackingNumber(),
                shipping.getStatus(),
                shipping.getRecipientName(),
                shipping.getAddress(),
                shipping.isSafePackagingRequested(),
                shipping.getShippedAt(),
                shipping.getDeliveredAt(),
                shipping.getCreatedAt());
    }
}
