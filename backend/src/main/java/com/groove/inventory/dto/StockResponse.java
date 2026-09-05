package com.groove.inventory.dto;

import com.groove.inventory.entity.Stock;
import com.groove.product.entity.ProductStatus;

public record StockResponse(
		Long productId,
		int quantity,
		ProductStatus productStatus
) {

	public static StockResponse from(Stock stock) {
		return new StockResponse(stock.getProduct().getId(), stock.getQuantity(), stock.getProduct().getStatus());
	}
}
