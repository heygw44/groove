package com.groove.cart.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.cart.dto.CartItemAddRequest;
import com.groove.cart.dto.CartItemQuantityUpdateRequest;
import com.groove.cart.dto.CartItemResponse;
import com.groove.cart.dto.CartResponse;
import com.groove.cart.entity.Cart;
import com.groove.cart.entity.CartItem;
import com.groove.cart.repository.CartItemRepository;
import com.groove.cart.repository.CartRepository;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductImage;
import com.groove.product.repository.ProductImageRepository;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/** 장바구니 조회/담기/수량변경/삭제. 담기·수량변경은 수량 상한(엔티티) 검사 후 재고(서비스) 검사 순으로 처리한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CartService {

	private static final int THUMBNAIL_SORT_ORDER = 0;

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final MemberRepository memberRepository;
	private final ProductRepository productRepository;
	private final ProductImageRepository productImageRepository;
	private final StockRepository stockRepository;
	private final LimitedDropRepository limitedDropRepository;

	public CartResponse getCart(Long memberId) {
		Cart cart = cartRepository.findByMemberId(memberId).orElse(null);
		if (cart == null) {
			return new CartResponse(null, List.of(), BigDecimal.ZERO);
		}

		List<CartItem> cartItems = cartItemRepository.findAllByCartIdOrderByIdAsc(cart.getId());
		List<Long> productIds = cartItems.stream()
				.map(item -> item.getProduct().getId())
				.distinct()
				.toList();
		Map<Long, String> thumbnails = productImageRepository
				.findAllByProductIdInAndSortOrder(productIds, THUMBNAIL_SORT_ORDER).stream()
				.collect(Collectors.toMap(image -> image.getProduct().getId(), ProductImage::getImageUrl,
						(first, second) -> first));
		Map<Long, Integer> stockQuantities = stockRepository.findAllByProductIdIn(productIds).stream()
				.collect(Collectors.toMap(stock -> stock.getProduct().getId(), Stock::getQuantity));

		List<CartItemResponse> responses = cartItems.stream()
				.map(item -> CartItemResponse.from(item,
						thumbnails.get(item.getProduct().getId()),
						stockQuantities.getOrDefault(item.getProduct().getId(), 0)))
				.toList();
		return CartResponse.of(cart, responses);
	}

	@Transactional
	public CartItemResponse addItem(Long memberId, CartItemAddRequest request) {
		Member member = findActiveMember(memberId);
		Product product = productRepository.findById(request.productId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		if (product.isHidden()) {
			throw new BusinessException(ErrorCode.PRODUCT_HIDDEN);
		}
		if (limitedDropRepository.existsByProductIdAndStatusNot(product.getId(), LimitedDropStatus.CLOSED)) {
			throw new BusinessException(ErrorCode.PRODUCT_LIMITED_ONLY);
		}

		Cart cart = getOrCreateCart(member);
		CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
				.map(existing -> {
					existing.addQuantity(request.quantity());
					return existing;
				})
				.orElseGet(() -> cartItemRepository.save(CartItem.create(cart, product, request.quantity())));

		int stockQuantity = findStockQuantity(product.getId());
		validateStock(item.getQuantity(), stockQuantity);
		return CartItemResponse.from(item, findThumbnailUrl(product.getId()), stockQuantity);
	}

	@Transactional
	public CartItemResponse updateQuantity(Long memberId, Long cartItemId, CartItemQuantityUpdateRequest request) {
		CartItem item = findOwned(memberId, cartItemId);
		item.changeQuantity(request.quantity());

		int stockQuantity = findStockQuantity(item.getProduct().getId());
		validateStock(item.getQuantity(), stockQuantity);
		return CartItemResponse.from(item, findThumbnailUrl(item.getProduct().getId()), stockQuantity);
	}

	@Transactional
	public void removeItem(Long memberId, Long cartItemId) {
		CartItem item = findOwned(memberId, cartItemId);
		cartItemRepository.delete(item);
	}

	@Transactional
	public void clear(Long memberId) {
		cartRepository.findByMemberId(memberId)
				.ifPresent(cart -> cartItemRepository.deleteAllByCartId(cart.getId()));
	}

	private Cart getOrCreateCart(Member member) {
		return cartRepository.findByMemberId(member.getId())
				.orElseGet(() -> cartRepository.save(Cart.create(member)));
	}

	private void validateStock(int quantity, int stockQuantity) {
		if (stockQuantity <= 0 || quantity > stockQuantity) {
			throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
		}
	}

	private int findStockQuantity(Long productId) {
		return stockRepository.findByProductId(productId).map(Stock::getQuantity).orElse(0);
	}

	private String findThumbnailUrl(Long productId) {
		return productImageRepository.findAllByProductIdInAndSortOrder(List.of(productId), THUMBNAIL_SORT_ORDER)
				.stream()
				.findFirst()
				.map(ProductImage::getImageUrl)
				.orElse(null);
	}

	private Member findActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		member.validateActive();
		return member;
	}

	private CartItem findOwned(Long memberId, Long cartItemId) {
		return cartItemRepository.findByIdAndCartMemberId(cartItemId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
	}
}
