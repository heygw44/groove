package com.groove.wishlist.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;
import com.groove.wishlist.entity.Wishlist;

public record WishlistItemResponse(
		Long id,
		Long productId,
		String title,
		String artistName,
		String thumbnailUrl,
		BigDecimal price,
		ProductStatus productStatus,
		int stockQuantity,
		LocalDateTime createdAt
) {

	public static WishlistItemResponse from(Wishlist wishlist, String thumbnailUrl, int stockQuantity) {
		Product product = wishlist.getProduct();
		return new WishlistItemResponse(
				wishlist.getId(),
				product.getId(),
				product.getTitle(),
				product.getArtist().getName(),
				thumbnailUrl,
				product.getPrice(),
				product.getStatus(),
				stockQuantity,
				wishlist.getCreatedAt());
	}
}
