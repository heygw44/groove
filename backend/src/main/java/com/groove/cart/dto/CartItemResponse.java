package com.groove.cart.dto;

import java.math.BigDecimal;

import com.groove.cart.entity.CartItem;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;

public record CartItemResponse(
		Long id,
		Long productId,
		String title,
		String artistName,
		String thumbnailUrl,
		BigDecimal price,
		ProductStatus productStatus,
		int stockQuantity,
		int quantity,
		BigDecimal subtotal
) {

	public static CartItemResponse from(CartItem item, String thumbnailUrl, int stockQuantity) {
		Product product = item.getProduct();
		BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
		return new CartItemResponse(
				item.getId(),
				product.getId(),
				product.getTitle(),
				product.getArtist().getName(),
				thumbnailUrl,
				product.getPrice(),
				product.getStatus(),
				stockQuantity,
				item.getQuantity(),
				subtotal);
	}
}
