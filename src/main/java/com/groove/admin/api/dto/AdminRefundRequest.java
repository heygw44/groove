package com.groove.admin.api.dto;

import jakarta.validation.constraints.Size;

/**
 * 관리자 환불 요청 (이슈 #69, POST /api/v1/admin/orders/{orderNumber}/refund).
 *
 * <p>{@code reason} 은 선택 — 지정 시 PG 환불 사유 및 {@code Order.cancelledReason} 으로 함께 기록된다.
 * 본문 자체를 생략한 요청도 허용한다(컨트롤러에서 {@code required = false}). 길이 상한은
 * {@link AdminOrderStatusChangeRequest#MAX_REASON_LENGTH} 와 동일하다 (DB 컬럼 길이 정렬).
 */
public record AdminRefundRequest(@Size(max = AdminOrderStatusChangeRequest.MAX_REASON_LENGTH) String reason) {
}
