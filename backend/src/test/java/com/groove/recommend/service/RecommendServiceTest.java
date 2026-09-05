package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.TasteProfileFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.ProductStatus;
import com.groove.recommend.dto.HomeRecommendResponse;
import com.groove.recommend.dto.ProductFeatureRow;
import com.groove.recommend.dto.RecommendItemResponse;
import com.groove.recommend.dto.RecommendReason;
import com.groove.recommend.entity.Decade;
import com.groove.recommend.entity.MemberTasteArtist;
import com.groove.recommend.entity.MemberTasteDecade;
import com.groove.recommend.entity.MemberTasteGenre;
import com.groove.recommend.entity.MemberTasteProfile;
import com.groove.recommend.mapper.RecommendQueryMapper;
import com.groove.recommend.repository.MemberTasteArtistRepository;
import com.groove.recommend.repository.MemberTasteDecadeRepository;
import com.groove.recommend.repository.MemberTasteGenreRepository;
import com.groove.recommend.repository.MemberTasteProfileRepository;
import com.groove.wishlist.repository.WishlistRepository;

@ExtendWith(MockitoExtension.class)
class RecommendServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long PROFILE_ID = 10L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 10, 0);

	@Mock
	private RecommendQueryMapper recommendQueryMapper;

	@Mock
	private BoughtTogetherRedisService boughtTogetherRedisService;

	@Mock
	private RecentViewService recentViewService;

	@Mock
	private WishlistRepository wishlistRepository;

	@Mock
	private OrderItemRepository orderItemRepository;

	@Mock
	private MemberTasteProfileRepository memberTasteProfileRepository;

	@Mock
	private MemberTasteGenreRepository memberTasteGenreRepository;

	@Mock
	private MemberTasteArtistRepository memberTasteArtistRepository;

	@Mock
	private MemberTasteDecadeRepository memberTasteDecadeRepository;

	private RecommendService recommendService;

	@BeforeEach
	void setUp() {
		recommendService = new RecommendService(recommendQueryMapper, new RecommendScorer(),
				boughtTogetherRedisService, recentViewService, wishlistRepository, orderItemRepository,
				memberTasteProfileRepository, memberTasteGenreRepository, memberTasteArtistRepository,
				memberTasteDecadeRepository);
	}

	private void givenNoSeeds() {
		given(wishlistRepository.findProductIdsByMemberId(MEMBER_ID)).willReturn(List.of());
		given(orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(eq(MEMBER_ID), any()))
				.willReturn(List.of());
		given(recentViewService.findRecentProductIds(MEMBER_ID)).willReturn(List.of());
	}

	private void givenNoTasteProfile(Long memberId) {
		given(memberTasteProfileRepository.findByMemberId(memberId)).willReturn(Optional.empty());
	}

	private void givenTasteProfile(Long memberId, Set<Long> artistIds, Set<Long> genreIds, Set<Decade> decades) {
		Member member = MemberFixture.withId(MemberFixture.create(), memberId);
		MemberTasteProfile profile = TasteProfileFixture.withId(TasteProfileFixture.create(member), PROFILE_ID);
		given(memberTasteProfileRepository.findByMemberId(memberId)).willReturn(Optional.of(profile));

		List<MemberTasteArtist> artists = artistIds.stream()
				.map(id -> MemberTasteArtist.of(profile, ArtistFixture.withId(id)))
				.toList();
		given(memberTasteArtistRepository.findAllByProfileId(PROFILE_ID)).willReturn(artists);

		List<MemberTasteGenre> genres = genreIds.stream()
				.map(id -> MemberTasteGenre.of(profile, GenreFixture.withId(GenreFixture.create("genre" + id), id)))
				.toList();
		given(memberTasteGenreRepository.findAllByProfileId(PROFILE_ID)).willReturn(genres);

		List<MemberTasteDecade> tasteDecades = decades.stream()
				.map(decade -> MemberTasteDecade.of(profile, decade))
				.toList();
		given(memberTasteDecadeRepository.findAllByProfileId(PROFILE_ID)).willReturn(tasteDecades);
	}

	private static ProductFeatureRow row(Long id, Long artistId, ProductStatus status, Double averageRating,
			LocalDateTime createdAt) {
		return new ProductFeatureRow(id, artistId, null, null, averageRating, createdAt, status, null);
	}

	private static ProductSummaryResponse summary(Long id) {
		return new ProductSummaryResponse(id, "title" + id, "artist", null, BigDecimal.ONE, null, null,
				ProductStatus.ON_SALE, null, null, 0L, null);
	}

	@Nested
	@DisplayName("recommendHome()")
	class RecommendHome {

		@Test
		@DisplayName("취향 프로필도 시드도 없으면 profileRequired 를 true 로 반환하고 후보를 조회하지 않는다")
		void requiresProfileWhenNoTasteAndNoSeeds() {
			// given
			givenNoSeeds();
			givenNoTasteProfile(MEMBER_ID);

			// when
			HomeRecommendResponse response = recommendService.recommendHome(MEMBER_ID, null);

			// then
			assertThat(response.profileRequired()).isTrue();
			assertThat(response.items()).isEmpty();
			verify(recommendQueryMapper, never()).findProductFeatures();
		}

		@Test
		@DisplayName("취향 프로필만 있으면 TASTE_ARTIST 이유로 추천하고 profileRequired 는 false 다")
		void recommendsByTasteWhenProfileOnly() {
			// given
			givenNoSeeds();
			givenTasteProfile(MEMBER_ID, Set.of(5L), Set.of(), Set.of());
			given(recommendQueryMapper.findProductFeatures())
					.willReturn(List.of(row(50L, 5L, ProductStatus.ON_SALE, 4.0, NOW)));
			given(recommendQueryMapper.findSummariesByIds(List.of(50L), MEMBER_ID))
					.willReturn(List.of(summary(50L)));

			// when
			HomeRecommendResponse response = recommendService.recommendHome(MEMBER_ID, null);

			// then
			assertThat(response.profileRequired()).isFalse();
			assertThat(response.items()).extracting(item -> item.product().id()).containsExactly(50L);
			assertThat(response.items().get(0).reasons()).containsExactly(RecommendReason.TASTE_ARTIST);
		}

		@Test
		@DisplayName("취향 프로필 없이 시드만 있으면 SAME_ARTIST 이유로 추천한다")
		void recommendsBySeedWhenSeedOnly() {
			// given
			givenNoTasteProfile(MEMBER_ID);
			given(wishlistRepository.findProductIdsByMemberId(MEMBER_ID)).willReturn(List.of(100L));
			given(orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(eq(MEMBER_ID), any()))
					.willReturn(List.of());
			given(recentViewService.findRecentProductIds(MEMBER_ID)).willReturn(List.of());
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(
					row(100L, 7L, ProductStatus.ON_SALE, null, NOW),
					row(200L, 7L, ProductStatus.ON_SALE, null, NOW)));
			given(boughtTogetherRedisService.findScores(Set.of(100L))).willReturn(Map.of());
			given(recommendQueryMapper.findSummariesByIds(List.of(200L), MEMBER_ID))
					.willReturn(List.of(summary(200L)));

			// when
			HomeRecommendResponse response = recommendService.recommendHome(MEMBER_ID, null);

			// then
			assertThat(response.items()).extracting(item -> item.product().id()).containsExactly(200L);
			assertThat(response.items().get(0).reasons()).containsExactly(RecommendReason.SAME_ARTIST);
		}

		@Test
		@DisplayName("HIDDEN 상품과 이미 구매·위시한 상품은 후보에서 제외된다")
		void excludesHiddenAndOwnedProducts() {
			// given
			givenTasteProfile(MEMBER_ID, Set.of(1L), Set.of(), Set.of());
			given(wishlistRepository.findProductIdsByMemberId(MEMBER_ID)).willReturn(List.of(10L));
			given(orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(eq(MEMBER_ID), any()))
					.willReturn(List.of(20L));
			given(recentViewService.findRecentProductIds(MEMBER_ID)).willReturn(List.of());
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(
					row(10L, 1L, ProductStatus.ON_SALE, null, NOW),
					row(20L, 1L, ProductStatus.ON_SALE, null, NOW),
					row(30L, 1L, ProductStatus.HIDDEN, null, NOW),
					row(40L, 1L, ProductStatus.ON_SALE, null, NOW)));
			given(boughtTogetherRedisService.findScores(Set.of(10L, 20L))).willReturn(Map.of());
			given(recommendQueryMapper.findSummariesByIds(List.of(40L), MEMBER_ID))
					.willReturn(List.of(summary(40L)));

			// when
			HomeRecommendResponse response = recommendService.recommendHome(MEMBER_ID, null);

			// then
			assertThat(response.items()).extracting(item -> item.product().id()).containsExactly(40L);
		}

		@Test
		@DisplayName("최근 본 상품하고만 매칭되면 RECENTLY_VIEWED_SIMILAR 이유를 붙인다")
		void marksRecentlyViewedSimilarWhenOnlyRecentSeedMatches() {
			// given
			givenNoTasteProfile(MEMBER_ID);
			given(wishlistRepository.findProductIdsByMemberId(MEMBER_ID)).willReturn(List.of());
			given(orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(eq(MEMBER_ID), any()))
					.willReturn(List.of());
			given(recentViewService.findRecentProductIds(MEMBER_ID)).willReturn(List.of(300L));
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(
					row(300L, 9L, ProductStatus.ON_SALE, null, NOW),
					row(400L, 9L, ProductStatus.ON_SALE, null, NOW)));
			given(boughtTogetherRedisService.findScores(Set.of(300L))).willReturn(Map.of());
			given(recommendQueryMapper.findSummariesByIds(List.of(400L), MEMBER_ID))
					.willReturn(List.of(summary(400L)));

			// when
			HomeRecommendResponse response = recommendService.recommendHome(MEMBER_ID, null);

			// then
			assertThat(response.items()).extracting(item -> item.product().id()).containsExactly(400L);
			assertThat(response.items().get(0).reasons()).containsExactly(RecommendReason.RECENTLY_VIEWED_SIMILAR);
		}

		@Test
		@DisplayName("공동구매 점수가 있으면 콘텐츠 매칭이 없어도 BOUGHT_TOGETHER 이유로 가산한다")
		void addsBoughtTogetherScore() {
			// given
			givenNoTasteProfile(MEMBER_ID);
			given(wishlistRepository.findProductIdsByMemberId(MEMBER_ID)).willReturn(List.of(500L));
			given(orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(eq(MEMBER_ID), any()))
					.willReturn(List.of());
			given(recentViewService.findRecentProductIds(MEMBER_ID)).willReturn(List.of());
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(
					row(500L, 1L, ProductStatus.ON_SALE, null, NOW),
					row(600L, 2L, ProductStatus.ON_SALE, null, NOW)));
			given(boughtTogetherRedisService.findScores(Set.of(500L)))
					.willReturn(Map.of(500L, Map.of(600L, 3.0)));
			given(recommendQueryMapper.findSummariesByIds(List.of(600L), MEMBER_ID))
					.willReturn(List.of(summary(600L)));

			// when
			HomeRecommendResponse response = recommendService.recommendHome(MEMBER_ID, null);

			// then
			assertThat(response.items().get(0).reasons()).containsExactly(RecommendReason.BOUGHT_TOGETHER);
		}

		@Test
		@DisplayName("총점이 같으면 평점 내림차순, 그다음 생성일 내림차순으로 정렬한다")
		void sortsByRatingThenCreatedAtWhenTotalScoreTied() {
			// given
			givenNoSeeds();
			givenTasteProfile(MEMBER_ID, Set.of(1L), Set.of(), Set.of());
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(
					row(700L, 1L, ProductStatus.ON_SALE, null, NOW),
					row(800L, 1L, ProductStatus.ON_SALE, 4.5, NOW.minusDays(1)),
					row(900L, 1L, ProductStatus.ON_SALE, 4.5, NOW)));
			ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.captor();
			given(recommendQueryMapper.findSummariesByIds(idsCaptor.capture(), eq(MEMBER_ID)))
					.willReturn(List.of(summary(900L), summary(800L), summary(700L)));

			// when
			HomeRecommendResponse response = recommendService.recommendHome(MEMBER_ID, null);

			// then
			assertThat(response.items()).extracting(item -> item.product().id())
					.containsExactly(900L, 800L, 700L);
			assertThat(idsCaptor.getValue()).containsExactly(900L, 800L, 700L);
		}

		@Test
		@DisplayName("Redis findScores 가 빈 맵이면 콘텐츠 점수만으로 동작한다")
		void worksWithContentScoreOnlyWhenRedisEmpty() {
			// given
			givenNoTasteProfile(MEMBER_ID);
			given(wishlistRepository.findProductIdsByMemberId(MEMBER_ID)).willReturn(List.of(100L));
			given(orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(eq(MEMBER_ID), any()))
					.willReturn(List.of());
			given(recentViewService.findRecentProductIds(MEMBER_ID)).willReturn(List.of());
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(
					row(100L, 7L, ProductStatus.ON_SALE, null, NOW),
					row(200L, 7L, ProductStatus.ON_SALE, null, NOW)));
			given(boughtTogetherRedisService.findScores(Set.of(100L))).willReturn(Map.of());
			given(recommendQueryMapper.findSummariesByIds(List.of(200L), MEMBER_ID))
					.willReturn(List.of(summary(200L)));

			// when
			HomeRecommendResponse response = recommendService.recommendHome(MEMBER_ID, null);

			// then
			assertThat(response.items()).extracting(item -> item.product().id()).containsExactly(200L);
		}

		@Test
		@DisplayName("요약 조회에서 빠진 id 는 결과에서 제외된다")
		void excludesIdsMissingFromSummaries() {
			// given
			givenNoSeeds();
			givenTasteProfile(MEMBER_ID, Set.of(1L), Set.of(), Set.of());
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(
					row(700L, 1L, ProductStatus.ON_SALE, 4.5, NOW),
					row(800L, 1L, ProductStatus.ON_SALE, 3.0, NOW)));
			given(recommendQueryMapper.findSummariesByIds(List.of(700L, 800L), MEMBER_ID))
					.willReturn(List.of(summary(800L)));

			// when
			HomeRecommendResponse response = recommendService.recommendHome(MEMBER_ID, null);

			// then
			assertThat(response.items()).extracting(item -> item.product().id()).containsExactly(800L);
		}

		@Test
		@DisplayName("size 가 최대치를 초과하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenSizeExceedsMax() {
			// when & then
			assertThatThrownBy(() -> recommendService.recommendHome(MEMBER_ID, 31))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
			verify(recommendQueryMapper, never()).findProductFeatures();
		}
	}

	@Nested
	@DisplayName("recommendRelated()")
	class RecommendRelated {

		private static final long PRODUCT_ID = 50L;

		@Test
		@DisplayName("기준 상품이 없으면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenProductNotFound() {
			// given
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of());

			// when & then
			assertThatThrownBy(() -> recommendService.recommendRelated(PRODUCT_ID, null, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
		}

		@Test
		@DisplayName("기준 상품이 HIDDEN 이면 PRODUCT_HIDDEN 예외를 던진다")
		void throwsWhenProductHidden() {
			// given
			given(recommendQueryMapper.findProductFeatures())
					.willReturn(List.of(row(PRODUCT_ID, 1L, ProductStatus.HIDDEN, null, NOW)));

			// when & then
			assertThatThrownBy(() -> recommendService.recommendRelated(PRODUCT_ID, null, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_HIDDEN);
		}

		@Test
		@DisplayName("비로그인은 SAME_* 이유만 추천하고 위시·구매·공동구매 조회는 호출하지 않는다")
		void recommendsBySameDimensionOnlyWhenAnonymous() {
			// given
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(
					row(PRODUCT_ID, 1L, ProductStatus.ON_SALE, null, NOW),
					row(60L, 1L, ProductStatus.ON_SALE, null, NOW)));
			given(recommendQueryMapper.findSummariesByIds(List.of(60L), null))
					.willReturn(List.of(summary(60L)));

			// when
			List<RecommendItemResponse> items = recommendService.recommendRelated(PRODUCT_ID, null, null);

			// then
			assertThat(items).extracting(item -> item.product().id()).containsExactly(60L);
			assertThat(items.get(0).reasons()).containsExactly(RecommendReason.SAME_ARTIST);
			verify(wishlistRepository, never()).findProductIdsByMemberId(any());
			verify(orderItemRepository, never()).findProductIdsByMemberIdAndOrderStatusIn(any(), any());
			verify(boughtTogetherRedisService, never()).findScores(any(Long.class));
			verify(memberTasteProfileRepository, never()).findByMemberId(any());
		}

		@Test
		@DisplayName("로그인 시 취향 매칭과 공동구매 이유가 함께 나온다")
		void mixesTasteAndBoughtTogetherWhenLoggedIn() {
			// given
			givenTasteProfile(MEMBER_ID, Set.of(2L), Set.of(), Set.of());
			given(wishlistRepository.findProductIdsByMemberId(MEMBER_ID)).willReturn(List.of());
			given(orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(eq(MEMBER_ID), any()))
					.willReturn(List.of());
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(
					row(PRODUCT_ID, 9L, ProductStatus.ON_SALE, null, NOW),
					row(70L, 2L, ProductStatus.ON_SALE, null, NOW),
					row(80L, 99L, ProductStatus.ON_SALE, null, NOW)));
			given(boughtTogetherRedisService.findScores(PRODUCT_ID)).willReturn(Map.of(80L, 2.0));
			given(recommendQueryMapper.findSummariesByIds(List.of(70L, 80L), MEMBER_ID))
					.willReturn(List.of(summary(70L), summary(80L)));

			// when
			List<RecommendItemResponse> items = recommendService.recommendRelated(PRODUCT_ID, MEMBER_ID, null);

			// then
			assertThat(items).extracting(item -> item.product().id()).containsExactlyInAnyOrder(70L, 80L);
			assertThat(items).flatExtracting(RecommendItemResponse::reasons)
					.contains(RecommendReason.TASTE_ARTIST, RecommendReason.BOUGHT_TOGETHER);
		}

		@Test
		@DisplayName("기준 상품 자기 자신은 추천 결과에서 제외된다")
		void excludesTargetProductItself() {
			// given
			given(recommendQueryMapper.findProductFeatures())
					.willReturn(List.of(row(PRODUCT_ID, 1L, ProductStatus.ON_SALE, null, NOW)));

			// when
			List<RecommendItemResponse> items = recommendService.recommendRelated(PRODUCT_ID, null, null);

			// then
			assertThat(items).isEmpty();
			verify(recommendQueryMapper, never()).findSummariesByIds(any(), any());
		}

		@Test
		@DisplayName("size 가 기본값 8 을 넘으면 상위 8개만 반환한다")
		void limitsToDefaultSizeWhenSizeNull() {
			// given
			List<ProductFeatureRow> rows = new ArrayList<>();
			rows.add(row(PRODUCT_ID, 1L, ProductStatus.ON_SALE, null, NOW));
			for (long id = 1L; id <= 9L; id++) {
				rows.add(row(id, 1L, ProductStatus.ON_SALE, null, NOW));
			}
			given(recommendQueryMapper.findProductFeatures()).willReturn(rows);
			given(recommendQueryMapper.findSummariesByIds(any(), eq(null))).willAnswer(invocation -> {
				List<Long> ids = invocation.getArgument(0);
				return ids.stream().map(RecommendServiceTest::summary).toList();
			});

			// when
			List<RecommendItemResponse> items = recommendService.recommendRelated(PRODUCT_ID, null, null);

			// then
			assertThat(items).hasSize(8);
		}

		@Test
		@DisplayName("size 가 최대치를 초과하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenSizeExceedsMax() {
			// when & then
			assertThatThrownBy(() -> recommendService.recommendRelated(PRODUCT_ID, null, 21))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
			verify(recommendQueryMapper, never()).findProductFeatures();
		}
	}
}
