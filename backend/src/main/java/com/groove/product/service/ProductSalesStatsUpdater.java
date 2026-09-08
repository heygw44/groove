package com.groove.product.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.groove.order.entity.Order;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/** 판매 수량 재계산을 호출하는 세 지점(결제 승인, 주문 취소, 관리자 상태 전이)의 중복을 없앤 협력자. */
@Service
@RequiredArgsConstructor
public class ProductSalesStatsUpdater {

	private final ProductRepository productRepository;

	public void refreshFor(Order order) {
		List<Long> productIds = order.getItems().stream()
				.map(item -> item.getProduct().getId())
				.distinct()
				// 같은 상품을 담은 주문이 동시에 갱신될 때 행 잠금 순서가 엇갈리면 데드락이 난다.
				// OrderStockService.lockStocks 와 같은 규칙으로 항상 id 오름차순으로 잠근다.
				.sorted()
				.toList();
		if (!productIds.isEmpty()) {
			productRepository.refreshSoldQuantities(productIds);
		}
	}
}
