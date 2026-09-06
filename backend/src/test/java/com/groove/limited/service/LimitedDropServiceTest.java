package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.dto.LimitedDropDetailResponse;
import com.groove.limited.dto.LimitedDropListResponse;
import com.groove.limited.dto.LimitedDropSummaryRow;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.product.dto.ProductDetailResponse;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductImage;
import com.groove.product.repository.ProductImageRepository;
import com.groove.recommend.dto.RecommendReason;
import com.groove.recommend.dto.TasteMatchResponse;
import com.groove.recommend.service.RecommendService;

@ExtendWith(MockitoExtension.class)
class LimitedDropServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private LimitedDropRepository limitedDropRepository;

	@Mock
	private LimitedPurchaseRepository limitedPurchaseRepository;

	@Mock
	private ProductImageRepository productImageRepository;

	@Mock
	private LimitedDropRedisService limitedDropRedisService;

	@Mock
	private RecommendService recommendService;

	private Clock clock;

	private LimitedDropService limitedDropService;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		limitedDropService = new LimitedDropService(limitedDropRepository, limitedPurchaseRepository,
				productImageRepository, limitedDropRedisService, recommendService, clock);
	}

	@Nested
	@DisplayName("getList()")
	class GetList {

		@Test
		@DisplayName("status 가 없으면 SCHEDULED·OPEN·SOLD_OUT 을 기본 필터로 조회한다")
		void queriesDefaultStatusesWhenStatusAbsent() {
			// given
			given(limitedDropRepository.findPublicSummaries(any())).willReturn(List.of());
			ArgumentCaptor<List<LimitedDropStatus>> captor = ArgumentCaptor.forClass(List.class);

			// when
			limitedDropService.getList(null, null);

			// then
			verify(limitedDropRepository).findPublicSummaries(captor.capture());
			assertThat(captor.getValue()).containsExactlyInAnyOrder(
					LimitedDropStatus.SCHEDULED, LimitedDropStatus.OPEN, LimitedDropStatus.SOLD_OUT);
		}

		@Test
		@DisplayName("status 를 지정하면 해당 상태 하나만 조회한다")
		void queriesSingleStatusWhenStatusGiven() {
			// given
			given(limitedDropRepository.findPublicSummaries(any())).willReturn(List.of());
			ArgumentCaptor<List<LimitedDropStatus>> captor = ArgumentCaptor.forClass(List.class);

			// when
			limitedDropService.getList(LimitedDropStatus.SOLD_OUT, null);

			// then
			verify(limitedDropRepository).findPublicSummaries(captor.capture());
			assertThat(captor.getValue()).containsExactly(LimitedDropStatus.SOLD_OUT);
		}

		@Test
		@DisplayName("OPEN 인 드롭은 Redis 값을, SCHEDULED 는 DB 값을 남은 수량으로 쓴다")
		void usesRedisForOpenAndDbForOthers() {
			// given
			LimitedDropSummaryRow openRow = summaryRow(1L, LimitedDropStatus.OPEN, 100, 20);
			LimitedDropSummaryRow scheduledRow = summaryRow(2L, LimitedDropStatus.SCHEDULED, 50, 0);
			given(limitedDropRepository.findPublicSummaries(any())).willReturn(List.of(openRow, scheduledRow));
			given(limitedDropRedisService.getStocks(List.of(1L))).willReturn(Map.of(1L, 55));

			// when
			LimitedDropListResponse response = limitedDropService.getList(null, null);

			// then
			assertThat(response.drops()).hasSize(2);
			assertThat(response.drops().get(0).remainingQuantity()).isEqualTo(55);
			assertThat(response.drops().get(1).remainingQuantity()).isEqualTo(50);
		}

		@Test
		@DisplayName("OPEN 인데 Redis 에 값이 없으면 DB 값으로 폴백한다")
		void fallsBackToDbWhenRedisMisses() {
			// given
			LimitedDropSummaryRow openRow = summaryRow(1L, LimitedDropStatus.OPEN, 100, 20);
			given(limitedDropRepository.findPublicSummaries(any())).willReturn(List.of(openRow));
			given(limitedDropRedisService.getStocks(List.of(1L))).willReturn(Map.of());

			// when
			LimitedDropListResponse response = limitedDropService.getList(null, null);

			// then
			assertThat(response.drops().get(0).remainingQuantity()).isEqualTo(80);
		}

		@Test
		@DisplayName("serverTime 은 주입된 Clock 기준 현재 시각이다")
		void returnsServerTimeFromClock() {
			// given
			given(limitedDropRepository.findPublicSummaries(any())).willReturn(List.of());

			// when
			LimitedDropListResponse response = limitedDropService.getList(null, null);

			// then
			assertThat(response.serverTime()).isEqualTo(OffsetDateTime.now(clock));
		}

		@Test
		@DisplayName("memberId 가 없으면 취향 매칭을 조회하지 않고 tasteMatch 는 null 이다")
		void skipsTasteMatchWhenMemberIdAbsent() {
			// given
			LimitedDropSummaryRow row = summaryRow(1L, LimitedDropStatus.OPEN, 100, 20);
			given(limitedDropRepository.findPublicSummaries(any())).willReturn(List.of(row));
			given(limitedDropRedisService.getStocks(any())).willReturn(Map.of());

			// when
			LimitedDropListResponse response = limitedDropService.getList(null, null);

			// then
			assertThat(response.drops().get(0).tasteMatch()).isNull();
			verifyNoInteractions(recommendService);
		}

		@Test
		@DisplayName("memberId 가 있으면 취향 매칭 결과를 각 드롭에 채운다")
		void fillsTasteMatchWhenMemberIdPresent() {
			// given
			LimitedDropSummaryRow row = summaryRow(1L, LimitedDropStatus.OPEN, 100, 20);
			given(limitedDropRepository.findPublicSummaries(any())).willReturn(List.of(row));
			given(limitedDropRedisService.getStocks(any())).willReturn(Map.of());
			TasteMatchResponse tasteMatch = new TasteMatchResponse(true, List.of(RecommendReason.TASTE_GENRE));
			given(recommendService.matchTaste(7L, List.of(1L))).willReturn(Map.of(1L, tasteMatch));

			// when
			LimitedDropListResponse response = limitedDropService.getList(null, 7L);

			// then
			assertThat(response.drops().get(0).tasteMatch()).isEqualTo(tasteMatch);
		}

		private LimitedDropSummaryRow summaryRow(Long id, LimitedDropStatus status, int totalQuantity,
				int soldCount) {
			return new LimitedDropSummaryRow(id, id, "상품" + id, "아티스트", new BigDecimal("10000"), null,
					totalQuantity, soldCount, 2, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
					status);
		}
	}

	@Nested
	@DisplayName("getDetail()")
	class GetDetail {

		@Test
		@DisplayName("존재하지 않으면 LIMITED_DROP_NOT_FOUND 예외를 던진다")
		void throwsWhenNotFound() {
			// given
			given(limitedDropRepository.findWithProductById(1L)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> limitedDropService.getDetail(1L, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_DROP_NOT_FOUND);
		}

		@Test
		@DisplayName("상품이 HIDDEN 이면 LIMITED_DROP_NOT_FOUND 예외를 던진다")
		void throwsWhenProductHidden() {
			// given
			Product product = product(10L);
			product.hide();
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), 1L);
			given(limitedDropRepository.findWithProductById(1L)).willReturn(Optional.of(drop));

			// when & then
			assertThatThrownBy(() -> limitedDropService.getDetail(1L, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_DROP_NOT_FOUND);
		}

		@Test
		@DisplayName("비로그인이면 purchased 가 null 이고 구매 여부를 조회하지 않는다")
		void returnsNullPurchasedWhenAnonymous() {
			// given
			Product product = product(11L);
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), 2L);
			given(limitedDropRepository.findWithProductById(2L)).willReturn(Optional.of(drop));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(11L)).willReturn(List.of());

			// when
			LimitedDropDetailResponse response = limitedDropService.getDetail(2L, null);

			// then
			assertThat(response.purchased()).isNull();
			verify(limitedPurchaseRepository, never()).existsByDropIdAndMemberId(anyLong(), anyLong());
		}

		@Test
		@DisplayName("로그인 상태면 구매 여부를 조회해 purchased 에 담는다")
		void returnsPurchasedWhenAuthenticated() {
			// given
			Product product = product(12L);
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), 3L);
			given(limitedDropRepository.findWithProductById(3L)).willReturn(Optional.of(drop));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(12L)).willReturn(List.of());
			given(limitedPurchaseRepository.existsByDropIdAndMemberId(3L, 7L)).willReturn(true);

			// when
			LimitedDropDetailResponse response = limitedDropService.getDetail(3L, 7L);

			// then
			assertThat(response.purchased()).isTrue();
		}

		@Test
		@DisplayName("memberId 가 없으면 취향 매칭을 조회하지 않고 tasteMatch 는 null 이다")
		void skipsTasteMatchWhenMemberIdAbsent() {
			// given
			Product product = product(14L);
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), 8L);
			given(limitedDropRepository.findWithProductById(8L)).willReturn(Optional.of(drop));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(14L)).willReturn(List.of());

			// when
			LimitedDropDetailResponse response = limitedDropService.getDetail(8L, null);

			// then
			assertThat(response.tasteMatch()).isNull();
			verifyNoInteractions(recommendService);
		}

		@Test
		@DisplayName("memberId 가 있으면 취향 매칭 결과를 tasteMatch 에 담는다")
		void fillsTasteMatchWhenMemberIdPresent() {
			// given
			Product product = product(15L);
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), 9L);
			given(limitedDropRepository.findWithProductById(9L)).willReturn(Optional.of(drop));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(15L)).willReturn(List.of());
			TasteMatchResponse tasteMatch = new TasteMatchResponse(true, List.of(RecommendReason.TASTE_GENRE));
			given(recommendService.matchTaste(7L, List.of(15L))).willReturn(Map.of(15L, tasteMatch));

			// when
			LimitedDropDetailResponse response = limitedDropService.getDetail(9L, 7L);

			// then
			assertThat(response.tasteMatch()).isEqualTo(tasteMatch);
		}

		@Test
		@DisplayName("썸네일 이미지가 있으면 첫 장을 사용한다")
		void usesFirstImageAsThumbnail() {
			// given
			Product product = product(13L);
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), 4L);
			given(limitedDropRepository.findWithProductById(4L)).willReturn(Optional.of(drop));
			ProductImage image = ProductImage.of(product, "https://cdn.groove.com/0.jpg", 0);
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(13L)).willReturn(List.of(image));

			// when
			LimitedDropDetailResponse response = limitedDropService.getDetail(4L, null);

			// then
			assertThat(response.product().thumbnailUrl()).isEqualTo("https://cdn.groove.com/0.jpg");
		}
	}

	@Nested
	@DisplayName("findSummaryForProduct()")
	class FindSummaryForProduct {

		@Test
		@DisplayName("드롭이 CLOSED 면 empty 를 반환한다")
		void returnsEmptyWhenClosed() {
			// given
			Product product = product(20L);
			LimitedDrop drop = LimitedDropFixture.withStatus(
					LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), 5L), LimitedDropStatus.CLOSED);
			given(limitedDropRepository.findByProductId(20L)).willReturn(Optional.of(drop));

			// when
			Optional<ProductDetailResponse.LimitedDropSummary> result = limitedDropService
					.findSummaryForProduct(20L);

			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("드롭이 활성 상태면 요약을 반환한다")
		void returnsSummaryWhenActive() {
			// given
			Product product = product(21L);
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.open(product, 50), 6L);
			given(limitedDropRepository.findByProductId(21L)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.getStock(6L)).willReturn(Optional.of(30));

			// when
			Optional<ProductDetailResponse.LimitedDropSummary> result = limitedDropService
					.findSummaryForProduct(21L);

			// then
			assertThat(result).isPresent();
			assertThat(result.get().remainingQuantity()).isEqualTo(30);
		}

		@Test
		@DisplayName("드롭이 없으면 empty 를 반환한다")
		void returnsEmptyWhenAbsent() {
			// given
			given(limitedDropRepository.findByProductId(22L)).willReturn(Optional.empty());

			// when
			Optional<ProductDetailResponse.LimitedDropSummary> result = limitedDropService
					.findSummaryForProduct(22L);

			// then
			assertThat(result).isEmpty();
		}
	}

	private static Product product(Long id) {
		Artist artist = ArtistFixture.withId(1L);
		return ProductFixture.withId(ProductFixture.create(artist), id);
	}
}
