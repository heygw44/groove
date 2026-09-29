package com.groove.order.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.groove.limited.service.LimitedPurchaseWriter;
import com.groove.limited.service.LimitedRelease;
import com.groove.limited.service.LimitedReleaseSynchronizer;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemStatus;
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
		finalizeOrderIfAllItemsCancelTerminal(order);
		Optional<LimitedRelease> limitedRelease = limitedPurchaseWriter.revertByOrder(order.getId(),
				LocalDateTime.now(clock));
		limitedRelease.ifPresent(limitedReleaseSynchronizer::releaseAfterCommit);
		if (paid) {
			productSalesStatsUpdater.refreshFor(order);
		}
		return limitedRelease;
	}

	/**
	 * 취소·반품 클레임이 확정된 상품주문 단위로 복원한다. {@code restock} 이 false 면(반품 완료 시 관리자가
	 * 재입고를 선택하지 않은 경우) 재고는 그대로 두고 쿠폰·한정반·판매량만 갱신한다. 쿠폰 복원·주문 취소 확정은
	 * 주문에 속한 모든 상품이 끝났을 때만 반영한다(D5) - 호출 전에 대상 상품주문의 상태 전이를 이미 반영해 둬야
	 * 한다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<LimitedRelease> restoreItems(Order order, List<OrderItem> items, boolean restock, boolean paid) {
		if (restock) {
			orderStockService.restore(items);
		}
		finalizeOrderIfAllItemsCancelTerminal(order);
		Optional<LimitedRelease> limitedRelease = limitedPurchaseWriter.revertByOrder(order.getId(),
				LocalDateTime.now(clock));
		limitedRelease.ifPresent(limitedReleaseSynchronizer::releaseAfterCommit);
		if (paid) {
			productSalesStatsUpdater.refreshFor(order);
		}
		return limitedRelease;
	}

	/**
	 * 주문에 속한 모든 상품주문이 취소·반품·미입금취소로 끝났을 때만(D5, {@link OrderItemStatus#CANCEL_TERMINAL})
	 * 쿠폰을 미사용 상태로 되돌리고 주문 자체도 취소로 확정한다. 구매확정(PURCHASE_CONFIRMED)은 성공적으로 끝난
	 * 상태라 이 판단에서 제외한다 - 한 주문에 구매확정된 상품과 방금 취소된 상품이 섞여 있을 수 있다.
	 */
	private void finalizeOrderIfAllItemsCancelTerminal(Order order) {
		if (!order.refreshAggregate(LocalDateTime.now(clock))) {
			return;
		}
		if (order.getMemberCoupon() != null && order.getMemberCoupon().isUsed()) {
			order.getMemberCoupon().restore();
		}
	}
}
