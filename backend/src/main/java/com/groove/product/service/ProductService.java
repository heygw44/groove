package com.groove.product.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.service.LimitedDropService;
import com.groove.product.dto.ProductDetailResponse;
import com.groove.product.dto.ProductSearchCondition;
import com.groove.product.dto.ProductSearchRequest;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductImage;
import com.groove.product.mapper.ProductSearchMapper;
import com.groove.product.repository.ProductImageRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.recommend.service.ProductViewedEvent;
import com.groove.wishlist.repository.WishlistRepository;

import lombok.RequiredArgsConstructor;

/** 상품 목록 검색·상세 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {

	private final ProductSearchMapper productSearchMapper;
	private final ProductRepository productRepository;
	private final ProductImageRepository productImageRepository;
	private final StockRepository stockRepository;
	private final WishlistRepository wishlistRepository;
	private final LimitedDropService limitedDropService;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public PageResponse<ProductSummaryResponse> search(ProductSearchRequest request, Long memberId) {
		ProductSearchCondition condition = request.toCondition(memberId);
		long totalElements = productSearchMapper.countProducts(condition);
		if (totalElements == 0) {
			return PageResponse.of(List.of(), condition.page(), condition.size(), 0);
		}
		List<ProductSummaryResponse> content = productSearchMapper.searchProducts(condition);
		return PageResponse.of(content, condition.page(), condition.size(), totalElements);
	}

	public ProductDetailResponse getDetail(Long id, Long memberId) {
		Product product = productRepository.findDetailById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		if (product.isHidden()) {
			throw new BusinessException(ErrorCode.PRODUCT_HIDDEN);
		}
		List<ProductImage> images = productImageRepository.findAllByProductIdOrderBySortOrderAsc(id);
		int stockQuantity = stockRepository.findByProductId(id)
				.map(Stock::getQuantity)
				.orElse(0);
		Boolean wishlisted = memberId == null ? null : wishlistRepository.existsByMemberIdAndProductId(memberId, id);
		ProductDetailResponse.LimitedDropSummary limitedDrop = limitedDropService.findSummaryForProduct(id)
				.orElse(null);
		ProductDetailResponse response = ProductDetailResponse.from(product, images, stockQuantity, wishlisted,
				limitedDrop);
		eventPublisher.publishEvent(new ProductViewedEvent(memberId, id, LocalDateTime.now(clock)));
		return response;
	}
}
