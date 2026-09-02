package com.groove.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.dto.StockResponse;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.entity.StockHistory;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.product.entity.Product;

import lombok.RequiredArgsConstructor;

/** 재고 조정. 낙관적 락 충돌(ObjectOptimisticLockingFailureException)은 커밋 시점에 나므로 여기서 잡지 않는다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StockService {

	private static final String INITIAL_STOCK_REASON = "초기 입고";

	private final StockRepository stockRepository;
	private final StockHistoryRepository stockHistoryRepository;

	@Transactional
	public Stock create(Product product, int initialQuantity) {
		Stock stock = stockRepository.save(Stock.create(product, initialQuantity));
		if (initialQuantity > 0) {
			stockHistoryRepository.save(
					StockHistory.of(stock, StockChangeType.IN, initialQuantity, INITIAL_STOCK_REASON));
		}
		return stock;
	}

	@Transactional
	public StockResponse adjust(Long productId, StockAdjustRequest request) {
		Stock stock = stockRepository.findWithProductByProductId(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

		int quantity = request.quantity();
		int delta = applyChange(stock, request.changeType(), quantity);

		// 이력 INSERT 가 stock 행에 FK 공유 락을 잡아 UPDATE 와 데드락이 나므로 재고 UPDATE 를 먼저 flush 한다.
		stockRepository.flush();
		stockHistoryRepository.save(StockHistory.of(stock, request.changeType(), delta, request.reason()));
		return StockResponse.from(stock);
	}

	public StockResponse getByProductId(Long productId) {
		Stock stock = stockRepository.findWithProductByProductId(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
		return StockResponse.from(stock);
	}

	private int applyChange(Stock stock, StockChangeType changeType, int quantity) {
		switch (changeType) {
			case IN:
				stock.increase(quantity);
				return quantity;
			case OUT:
				stock.decrease(quantity);
				return -quantity;
			case ADJUST:
				int delta = quantity - stock.getQuantity();
				stock.replaceQuantity(quantity);
				return delta;
			case CANCEL:
			default:
				throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
	}
}
