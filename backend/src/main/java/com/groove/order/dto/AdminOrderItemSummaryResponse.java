package com.groove.order.dto;

import java.time.LocalDateTime;

import com.groove.order.entity.CourierCode;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;

/**
 * 관리자 상품주문 목록 한 줄. 발주확인/발송처리/배송완료 대상 선택에 id 를 쓴다. {@code virtualAccountPayment} 는
 * 가상계좌 결제인지다 - 환불에 구매자 계좌가 필요해 관리자 판매취소를 막는 데 쓴다.
 */
public record AdminOrderItemSummaryResponse(
		Long id,
		Long orderId,
		String productOrderNumber,
		String orderNumber,
		String memberEmail,
		String productName,
		int quantity,
		OrderItemStatus status,
		OrderItemClaimStatus claimStatus,
		CourierCode courierCode,
		String trackingNumber,
		LocalDateTime createdAt,
		boolean virtualAccountPayment
) {
}
