package com.groove.order.dto;

/**
 * 관리자 상품주문 일괄 처리 한 번에 받는 최대 건수. 요청 건수만큼 주문 행을 FOR UPDATE 로 잡으므로, 상한이 없으면
 * 한 요청이 구매자 취소·클레임·스케줄러를 오래 막는다. 화면 페이지 크기(20)에 여유를 둔 값이다.
 */
public final class AdminOrderItemBulkLimits {

	public static final int MAX_ITEMS = 100;

	private AdminOrderItemBulkLimits() {
	}
}
