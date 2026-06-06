package com.groove.order.api.dto;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 주문 목록(요약) 응답 (API.md §3.5 PageResponse&lt;OrderSummary&gt;).
 *
 * <p>상세 응답({@link OrderResponse})과 달리 라인 단위 정보는 노출하지 않고
 * {@code itemCount} (라인 수, 수량 합 아님) 와 첫 라인의 앨범 제목 스냅샷만 내려준다 —
 * 실제 이커머스 주문 리스트가 "[Abbey Road] 외 N건" 형식으로 표기하는 패턴을 따른다.
 *
 * <p>"첫 라인" 의 정의는 {@link Order#addItem} 삽입 순 = {@code OrderItem.id ASC}.
 * {@link Order#getItems()} 가 ArrayList 라 삽입 순이 그대로 보존된다.
 */
public record OrderSummaryResponse(
        @Schema(description = "주문번호 (형식: ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260101-AB12CD")
        String orderNumber,
        @Schema(description = "주문 상태", example = "PAID")
        OrderStatus status,
        @Schema(description = "상품 합계 금액(할인 전, KRW)", example = "45000")
        long totalAmount,
        @Schema(description = "주문 라인 수(수량 합 아님)", example = "2")
        int itemCount,
        @Schema(description = "대표(첫 라인) 앨범명 스냅샷", example = "Abbey Road")
        String representativeAlbumTitle,
        @Schema(description = "주문 생성 시각")
        Instant createdAt
) {

    public static OrderSummaryResponse from(Order order) {
        List<OrderItem> items = order.getItems();
        String representative = items.isEmpty() ? null : items.get(0).getAlbumTitleSnapshot();
        return new OrderSummaryResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                items.size(),
                representative,
                order.getCreatedAt());
    }
}
