package com.groove.order.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 쿠폰 할인액을 라인 금액(단가 x 수량) 비율로 상품주문에 나눠 담는다. 원 미만은 버리고, 배분 후 남는 원 단위는
 * 라인 금액이 가장 큰 상품(동률이면 먼저 추가된, 즉 상품주문번호가 앞선 상품)에 더한다.
 */
final class DiscountAllocator {

	private DiscountAllocator() {
	}

	static void allocate(List<OrderItem> items, BigDecimal discountAmount) {
		if (items.isEmpty()) {
			return;
		}
		if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) == 0) {
			items.forEach(item -> item.applyDiscountShare(BigDecimal.ZERO));
			return;
		}

		BigDecimal totalAmount = items.stream()
				.map(OrderItem::getLineAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal allocated = BigDecimal.ZERO;
		OrderItem largest = null;
		for (OrderItem item : items) {
			BigDecimal lineAmount = item.getLineAmount();
			BigDecimal share = totalAmount.compareTo(BigDecimal.ZERO) == 0
					? BigDecimal.ZERO
					: discountAmount.multiply(lineAmount).divide(totalAmount, 0, RoundingMode.DOWN);
			item.applyDiscountShare(share);
			allocated = allocated.add(share);
			if (largest == null || lineAmount.compareTo(largest.getLineAmount()) > 0) {
				largest = item;
			}
		}

		BigDecimal remainder = discountAmount.subtract(allocated);
		if (largest != null && remainder.compareTo(BigDecimal.ZERO) > 0) {
			largest.applyDiscountShare(largest.getDiscountShare().add(remainder));
		}
	}
}
