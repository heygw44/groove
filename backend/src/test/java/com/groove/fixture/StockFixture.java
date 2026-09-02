package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.product.entity.Product;

public final class StockFixture {

	private static final int DEFAULT_QUANTITY = 10;

	private StockFixture() {
	}

	public static Stock create(Product product) {
		return create(product, DEFAULT_QUANTITY);
	}

	public static Stock create(Product product, int quantity) {
		return Stock.create(product, quantity);
	}

	public static Stock withId(Stock stock, Long id) {
		ReflectionTestUtils.setField(stock, "id", id);
		return stock;
	}

	public static StockAdjustRequest adjustRequest(StockChangeType changeType, int quantity) {
		return new StockAdjustRequest(changeType, quantity, "테스트 사유");
	}
}
