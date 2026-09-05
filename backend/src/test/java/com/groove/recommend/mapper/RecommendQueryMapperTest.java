package com.groove.recommend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ProductViewLogFixture;
import com.groove.member.entity.Member;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.recommend.entity.ProductViewLog;
import com.groove.support.MybatisTestSupport;
import com.groove.wishlist.entity.Wishlist;

import jakarta.persistence.EntityManager;

class RecommendQueryMapperTest extends MybatisTestSupport {

	@Autowired
	private RecommendQueryMapper recommendQueryMapper;

	@Autowired
	private EntityManager em;

	private Artist artist;
	private Product kindOfBlue;
	private Product loveSupreme;
	private Product hiddenAlbum;

	@BeforeEach
	void setUp() {
		artist = ArtistFixture.create("RQM Artist");
		em.persist(artist);

		kindOfBlue = ProductFixture.create(artist, "RQM Kind of Blue", new BigDecimal("30000.00"));
		loveSupreme = ProductFixture.create(artist, "RQM A Love Supreme", new BigDecimal("45000.00"));
		hiddenAlbum = ProductFixture.create(artist, "RQM Hidden Album", new BigDecimal("40000.00"));
		hiddenAlbum.hide();

		em.persist(kindOfBlue);
		em.persist(loveSupreme);
		em.persist(hiddenAlbum);
		em.flush();
		em.clear();
	}

	@Nested
	@DisplayName("findSummariesByIds()")
	class FindSummariesByIds {

		@Test
		@DisplayName("ids 순서와 무관하게 해당 상품들을 전부 조회한다")
		void findsAllRequestedIdsRegardlessOfOrder() {
			// given
			List<Long> ids = List.of(loveSupreme.getId(), kindOfBlue.getId());

			// when
			List<ProductSummaryResponse> result = recommendQueryMapper.findSummariesByIds(ids, null);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactlyInAnyOrder(kindOfBlue.getId(), loveSupreme.getId());
		}

		@Test
		@DisplayName("HIDDEN 상품은 제외한다")
		void excludesHiddenProducts() {
			// given
			List<Long> ids = List.of(kindOfBlue.getId(), hiddenAlbum.getId());

			// when
			List<ProductSummaryResponse> result = recommendQueryMapper.findSummariesByIds(ids, null);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(kindOfBlue.getId());
		}

		@Test
		@DisplayName("memberId 가 null 이면 wishlisted 가 null 이다")
		void wishlistedIsNullWhenMemberIdAbsent() {
			// given
			Member member = MemberFixture.create("rqm-wish@groove.com");
			em.persist(member);
			em.persist(Wishlist.create(member, kindOfBlue));
			em.flush();
			em.clear();

			// when
			List<ProductSummaryResponse> result = recommendQueryMapper.findSummariesByIds(
					List.of(kindOfBlue.getId()), null);

			// then
			assertThat(result).extracting(ProductSummaryResponse::wishlisted).containsExactly((Boolean) null);
		}

		@Test
		@DisplayName("memberId 가 있으면 위시 여부가 반영된다")
		void reflectsWishlistStatusWhenMemberIdPresent() {
			// given
			Member member = MemberFixture.create("rqm-wish2@groove.com");
			em.persist(member);
			em.persist(Wishlist.create(member, kindOfBlue));
			em.flush();
			em.clear();

			// when
			List<ProductSummaryResponse> result = recommendQueryMapper.findSummariesByIds(
					List.of(kindOfBlue.getId(), loveSupreme.getId()), member.getId());

			// then
			assertThat(result).filteredOn(r -> r.id().equals(kindOfBlue.getId()))
					.extracting(ProductSummaryResponse::wishlisted)
					.containsExactly(true);
			assertThat(result).filteredOn(r -> r.id().equals(loveSupreme.getId()))
					.extracting(ProductSummaryResponse::wishlisted)
					.containsExactly(false);
		}
	}

	@Nested
	@DisplayName("findRecentProductIds()")
	class FindRecentProductIds {

		@Test
		@DisplayName("폴백 쿼리가 상품별 최신 조회순으로 반환한다")
		void returnsProductIdsOrderedByLatestViewedAt() {
			// given
			Member member = MemberFixture.create("rqm-recent@groove.com");
			em.persist(member);

			LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
			persistView(member, kindOfBlue, now.minusMinutes(10));
			persistView(member, loveSupreme, now.minusMinutes(5));
			persistView(member, kindOfBlue, now.minusMinutes(1));
			em.flush();
			em.clear();

			// when
			List<Long> result = recommendQueryMapper.findRecentProductIds(member.getId(), 20);

			// then
			assertThat(result).containsExactly(kindOfBlue.getId(), loveSupreme.getId());
		}

		private void persistView(Member member, Product product, LocalDateTime viewedAt) {
			ProductViewLog viewLog = ProductViewLogFixture.create(
					em.find(Member.class, member.getId()), em.find(Product.class, product.getId()), viewedAt);
			em.persist(viewLog);
		}
	}
}
