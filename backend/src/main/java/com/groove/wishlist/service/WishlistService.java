package com.groove.wishlist.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductImage;
import com.groove.product.entity.ProductStatus;
import com.groove.product.repository.ProductImageRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.wishlist.dto.WishlistAddRequest;
import com.groove.wishlist.dto.WishlistItemResponse;
import com.groove.wishlist.dto.WishlistSearchRequest;
import com.groove.wishlist.entity.Wishlist;
import com.groove.wishlist.repository.WishlistRepository;

import lombok.RequiredArgsConstructor;

/** 위시리스트 조회/등록/삭제. HIDDEN 상품은 목록에서 제외한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WishlistService {

	private static final int THUMBNAIL_SORT_ORDER = 0;

	private final WishlistRepository wishlistRepository;
	private final MemberRepository memberRepository;
	private final ProductRepository productRepository;
	private final ProductImageRepository productImageRepository;
	private final StockRepository stockRepository;

	public PageResponse<WishlistItemResponse> getWishlist(Long memberId, WishlistSearchRequest request) {
		Page<Wishlist> page = wishlistRepository.findAllByMemberIdAndProductStatusNot(memberId,
				ProductStatus.HIDDEN, request.toPageable());
		if (page.isEmpty()) {
			return PageResponse.of(List.of(), page.getNumber(), page.getSize(), 0);
		}

		List<Long> productIds = page.getContent().stream()
				.map(wishlist -> wishlist.getProduct().getId())
				.distinct()
				.toList();
		Map<Long, String> thumbnails = productImageRepository
				.findAllByProductIdInAndSortOrder(productIds, THUMBNAIL_SORT_ORDER).stream()
				.collect(Collectors.toMap(image -> image.getProduct().getId(), ProductImage::getImageUrl,
						(first, second) -> first));
		Map<Long, Integer> stockQuantities = stockRepository.findAllByProductIdIn(productIds).stream()
				.collect(Collectors.toMap(stock -> stock.getProduct().getId(), Stock::getQuantity));

		return PageResponse.from(page.map(wishlist -> {
			Long productId = wishlist.getProduct().getId();
			return WishlistItemResponse.from(wishlist, thumbnails.get(productId),
					stockQuantities.getOrDefault(productId, 0));
		}));
	}

	@Transactional
	public WishlistItemResponse add(Long memberId, WishlistAddRequest request) {
		Member member = findActiveMember(memberId);
		Product product = productRepository.findById(request.productId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		if (product.isHidden()) {
			throw new BusinessException(ErrorCode.PRODUCT_HIDDEN);
		}
		if (wishlistRepository.existsByMemberIdAndProductId(memberId, product.getId())) {
			throw new BusinessException(ErrorCode.WISHLIST_ALREADY_EXISTS);
		}

		Wishlist saved;
		try {
			// 동시 요청은 unique 제약으로 막고 409 로 변환
			saved = wishlistRepository.saveAndFlush(Wishlist.create(member, product));
		} catch (DataIntegrityViolationException | CannotAcquireLockException e) {
			// InnoDB 는 같은 유니크 키로 INSERT 가 몰리면 중복키 대신 데드락을 내므로 락 획득 실패도 같이 잡는다.
			throw new BusinessException(ErrorCode.WISHLIST_ALREADY_EXISTS);
		}

		return WishlistItemResponse.from(saved, findThumbnailUrl(product.getId()),
				findStockQuantity(product.getId()));
	}

	@Transactional
	public void remove(Long memberId, Long productId) {
		Wishlist wishlist = wishlistRepository.findByMemberIdAndProductId(memberId, productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.WISHLIST_NOT_FOUND));
		wishlistRepository.delete(wishlist);
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
}
