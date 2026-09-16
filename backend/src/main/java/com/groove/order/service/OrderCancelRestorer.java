package com.groove.order.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.groove.limited.service.LimitedPurchaseWriter;
import com.groove.limited.service.LimitedRelease;
import com.groove.limited.service.LimitedReleaseSynchronizer;
import com.groove.order.entity.Order;
import com.groove.product.service.ProductSalesStatsUpdater;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderCancelRestorer {

	private final OrderStockService orderStockService;
	private final LimitedPurchaseWriter limitedPurchaseWriter;
	private final LimitedReleaseSynchronizer limitedReleaseSynchronizer;
	private final ProductSalesStatsUpdater productSalesStatsUpdater;
	private final Clock clock;

	/** 취소된 주문의 재고·쿠폰·한정반 선점을 되돌린다. 결제된 주문이면 판매량도 다시 계산한다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<LimitedRelease> restore(Order order, boolean paid) {
		orderStockService.restore(order);
		if (order.getMemberCoupon() != null && order.getMemberCoupon().isUsed()) {
			order.getMemberCoupon().restore();
		}
		Optional<LimitedRelease> limitedRelease = limitedPurchaseWriter.revertByOrder(order.getId(),
				LocalDateTime.now(clock));
		limitedRelease.ifPresent(limitedReleaseSynchronizer::releaseAfterCommit);
		if (paid) {
			productSalesStatsUpdater.refreshFor(order);
		}
		return limitedRelease;
	}
}
