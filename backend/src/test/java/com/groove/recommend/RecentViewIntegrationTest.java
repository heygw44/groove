package com.groove.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ProductViewLogFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.recommend.repository.ProductViewLogRepository;
import com.groove.recommend.service.RecentViewRedisService;
import com.groove.recommend.service.RecentViewService;
import com.groove.support.IntegrationTestSupport;

class RecentViewIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private RecentViewRedisService recentViewRedisService;

	@Autowired
	private RecentViewService recentViewService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ProductViewLogRepository productViewLogRepository;

	private Product createProduct() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		return productRepository.save(ProductFixture.create(artist));
	}

	private Member createMember() {
		return memberRepository.save(MemberFixture.create("recent-viewer-" + UUID.randomUUID() + "@groove.com"));
	}

	@Nested
	@DisplayName("push()")
	class Push {

		@Test
		@DisplayName("같은 상품을 다시 조회하면 중복 없이 목록 맨 앞으로 온다")
		void movesRepeatedProductToFrontWithoutDuplication() {
			// given
			Member member = createMember();
			Product productA = createProduct();
			Product productB = createProduct();

			// when
			recentViewRedisService.push(member.getId(), productA.getId());
			recentViewRedisService.push(member.getId(), productB.getId());
			recentViewRedisService.push(member.getId(), productA.getId());

			// then
			assertThat(recentViewRedisService.findRecentProductIds(member.getId()))
					.containsExactly(productA.getId(), productB.getId());
		}

		@Test
		@DisplayName("20개를 넘게 조회하면 오래된 항목이 잘려 20개만 남는다")
		void trimsOldestEntryWhenExceedingMaxSize() {
			// given
			Member member = createMember();
			String key = RecentViewRedisService.recentViewKey(member.getId());

			// when
			for (long productId = 1; productId <= 21; productId++) {
				recentViewRedisService.push(member.getId(), productId);
			}

			// then
			Long size = redisTemplate.opsForList().size(key);
			assertThat(size).isEqualTo(RecentViewRedisService.MAX_SIZE);
			assertThat(recentViewRedisService.findRecentProductIds(member.getId())).doesNotContain(1L);
		}

		@Test
		@DisplayName("적재하면 TTL 이 설정된다")
		void setsTtlOnPush() {
			// given
			Member member = createMember();
			Product product = createProduct();
			String key = RecentViewRedisService.recentViewKey(member.getId());

			// when
			recentViewRedisService.push(member.getId(), product.getId());

			// then
			Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
			assertThat(expire).isLessThanOrEqualTo(RecentViewRedisService.TTL.toSeconds());
			assertThat(expire).isGreaterThanOrEqualTo(RecentViewRedisService.TTL.toSeconds() - 1000);
		}
	}

	@Nested
	@DisplayName("getRecentViews()")
	class GetRecentViews {

		@Test
		@DisplayName("Redis 목록 순서대로 상품 요약을 반환한다")
		void returnsSummariesInRedisListOrder() {
			// given
			Member member = createMember();
			Product productA = createProduct();
			Product productB = createProduct();
			recentViewRedisService.push(member.getId(), productA.getId());
			recentViewRedisService.push(member.getId(), productB.getId());

			// when
			List<ProductSummaryResponse> result = recentViewService.getRecentViews(member.getId());

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactly(productB.getId(), productA.getId());
		}

		@Test
		@DisplayName("Redis 목록이 비면 조회 로그에서 폴백해 반환한다")
		void fallsBackToViewLogWhenRedisListIsEmpty() {
			// given
			Member member = createMember();
			Product product = createProduct();
			productViewLogRepository.saveAndFlush(ProductViewLogFixture.create(member, product,
					LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
			assertThat(redisTemplate.hasKey(RecentViewRedisService.recentViewKey(member.getId()))).isFalse();

			// when
			List<ProductSummaryResponse> result = recentViewService.getRecentViews(member.getId());

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(product.getId());
		}

		@Test
		@DisplayName("HIDDEN 상품은 응답에서 빠진다")
		void excludesHiddenProduct() {
			// given
			Member member = createMember();
			Product product = createProduct();
			product.hide();
			productRepository.saveAndFlush(product);
			recentViewRedisService.push(member.getId(), product.getId());

			// when
			List<ProductSummaryResponse> result = recentViewService.getRecentViews(member.getId());

			// then
			assertThat(result).isEmpty();
		}
	}
}
