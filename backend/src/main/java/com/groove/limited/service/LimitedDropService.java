package com.groove.limited.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.dto.LimitedDropDetailResponse;
import com.groove.limited.dto.LimitedDropListResponse;
import com.groove.limited.dto.LimitedDropSummaryResponse;
import com.groove.limited.dto.LimitedDropSummaryRow;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.product.dto.ProductDetailResponse;
import com.groove.product.entity.ProductImage;
import com.groove.product.repository.ProductImageRepository;

import lombok.RequiredArgsConstructor;

/** 한정반 공개 조회. 남은 수량은 OPEN 이면 Redis 카운터, 아니면 DB 값을 쓴다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LimitedDropService {

	private static final List<LimitedDropStatus> DEFAULT_STATUSES = List.copyOf(
			EnumSet.of(LimitedDropStatus.SCHEDULED, LimitedDropStatus.OPEN, LimitedDropStatus.SOLD_OUT));

	private final LimitedDropRepository limitedDropRepository;
	private final LimitedPurchaseRepository limitedPurchaseRepository;
	private final ProductImageRepository productImageRepository;
	private final LimitedDropRedisService limitedDropRedisService;
	private final Clock clock;

	public LimitedDropListResponse getList(LimitedDropStatus status) {
		List<LimitedDropStatus> statuses = status == null ? DEFAULT_STATUSES : List.of(status);
		List<LimitedDropSummaryRow> rows = limitedDropRepository.findPublicSummaries(statuses);

		List<Long> openDropIds = rows.stream()
				.filter(row -> row.status() == LimitedDropStatus.OPEN)
				.map(LimitedDropSummaryRow::id)
				.toList();
		Map<Long, Integer> redisStocks = limitedDropRedisService.getStocks(openDropIds);

		List<LimitedDropSummaryResponse> drops = rows.stream()
				.map(row -> {
					int remaining = row.status() == LimitedDropStatus.OPEN && redisStocks.containsKey(row.id())
							? redisStocks.get(row.id())
							: row.totalQuantity() - row.soldCount();
					return LimitedDropSummaryResponse.from(row, remaining, clock.getZone());
				})
				.toList();

		return new LimitedDropListResponse(drops, now());
	}

	public LimitedDropDetailResponse getDetail(Long id, Long memberId) {
		LimitedDrop drop = limitedDropRepository.findWithProductById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.LIMITED_DROP_NOT_FOUND));
		if (drop.getProduct().isHidden()) {
			throw new BusinessException(ErrorCode.LIMITED_DROP_NOT_FOUND);
		}

		String thumbnailUrl = productImageRepository.findAllByProductIdOrderBySortOrderAsc(drop.getProduct().getId())
				.stream()
				.findFirst()
				.map(ProductImage::getImageUrl)
				.orElse(null);
		int remainingQuantity = remainingQuantityOf(drop);
		Boolean purchased = memberId == null
				? null
				: limitedPurchaseRepository.existsByDropIdAndMemberId(id, memberId);

		return LimitedDropDetailResponse.from(drop, thumbnailUrl, remainingQuantity, purchased, now(),
				clock.getZone());
	}

	public int remainingQuantityOf(LimitedDrop drop) {
		if (drop.getStatus() != LimitedDropStatus.OPEN) {
			return drop.remainingQuantity();
		}
		return limitedDropRedisService.getStock(drop.getId()).orElseGet(drop::remainingQuantity);
	}

	/** 상품 상세에 붙는 한정반 요약. CLOSED 는 노출하지 않는다. */
	public Optional<ProductDetailResponse.LimitedDropSummary> findSummaryForProduct(Long productId) {
		return limitedDropRepository.findByProductId(productId)
				.filter(LimitedDrop::isActive)
				.map(drop -> new ProductDetailResponse.LimitedDropSummary(
						drop.getId(),
						drop.getStatus(),
						drop.getOpenAt().atZone(clock.getZone()).toOffsetDateTime(),
						drop.getCloseAt().atZone(clock.getZone()).toOffsetDateTime(),
						remainingQuantityOf(drop),
						drop.getPerMemberLimit()));
	}

	private OffsetDateTime now() {
		return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
	}
}
