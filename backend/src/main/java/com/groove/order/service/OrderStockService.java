package com.groove.order.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.entity.StockHistory;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 주문 생성/취소·관리자 상태 전이가 공유하는 재고 차감·복구 로직. 호출자 트랜잭션에 참여한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStockService {

	private static final String STOCK_OUT_REASON_PREFIX = "주문 ";
	private static final String STOCK_CANCEL_REASON_PREFIX = "주문 취소 ";

	private final StockRepository stockRepository;
	private final StockHistoryRepository stockHistoryRepository;

	@Transactional
	public void deduct(Order order) {
		Map<Long, Stock> stocksByProductId = lockStocks(order);
		for (OrderItem item : sortedItems(order)) {
			stocksByProductId.get(item.getProduct().getId()).decrease(item.getQuantity());
		}

		// 이력 INSERT 가 stock 행에 FK 공유 락을 잡아 UPDATE 와 데드락이 나므로 재고 UPDATE 를 먼저 flush 한다.
		flushStockUpdates();

		for (OrderItem item : sortedItems(order)) {
			Stock stock = stocksByProductId.get(item.getProduct().getId());
			stockHistoryRepository.save(StockHistory.of(stock, StockChangeType.OUT, -item.getQuantity(),
					STOCK_OUT_REASON_PREFIX + order.getOrderNumber()));
		}
	}

	@Transactional
	public void restore(Order order) {
		Map<Long, Stock> stocksByProductId = lockStocks(order);
		for (OrderItem item : sortedItems(order)) {
			stocksByProductId.get(item.getProduct().getId()).increase(item.getQuantity());
		}

		// 이력 INSERT 가 stock 행에 FK 공유 락을 잡아 UPDATE 와 데드락이 나므로 재고 UPDATE 를 먼저 flush 한다.
		flushStockUpdates();

		for (OrderItem item : sortedItems(order)) {
			Stock stock = stocksByProductId.get(item.getProduct().getId());
			stockHistoryRepository.save(StockHistory.of(stock, StockChangeType.CANCEL, item.getQuantity(),
					STOCK_CANCEL_REASON_PREFIX + order.getOrderNumber()));
		}
	}

	private List<OrderItem> sortedItems(Order order) {
		return order.getItems().stream()
				.sorted(Comparator.comparing(item -> item.getProduct().getId()))
				.toList();
	}

	// 재고 UPDATE 의 락 실패는 flush 시점에 드러난다. 전역 핸들러는 어떤 행이었는지 모르므로 여기서 재고 코드로 확정한다.
	private void flushStockUpdates() {
		try {
			stockRepository.flush();
		} catch (PessimisticLockingFailureException ex) {
			throw stockConflict(ex);
		}
	}

	private BusinessException stockConflict(PessimisticLockingFailureException ex) {
		// BusinessException 은 cause 를 싣지 않고 전역 핸들러도 스택을 남기지 않아, 타임아웃·데드락 구분은 여기서만 남는다.
		log.warn("stock row lock failed", ex);
		return new BusinessException(ErrorCode.STOCK_CONFLICT);
	}

	private Map<Long, Stock> lockStocks(Order order) {
		List<Long> productIds = order.getItems().stream()
				.map(item -> item.getProduct().getId())
				.distinct()
				.sorted()
				.toList();
		List<Stock> stocks;
		try {
			stocks = stockRepository.findAllWithProductByProductIdInForUpdate(productIds);
		} catch (PessimisticLockingFailureException ex) {
			throw stockConflict(ex);
		}
		if (stocks.size() != productIds.size()) {
			throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
		}
		return stocks.stream()
				.collect(Collectors.toMap(stock -> stock.getProduct().getId(), stock -> stock));
	}
}
