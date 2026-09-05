package com.groove.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.dto.ProductDetailResponse;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.product.service.ProductService;
import com.groove.recommend.entity.ProductViewLog;
import com.groove.recommend.repository.ProductViewLogRepository;
import com.groove.recommend.service.ProductViewLogSaver;
import com.groove.recommend.service.RecentViewRedisService;
import com.groove.support.IntegrationTestSupport;

class ProductViewLogWriterIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ProductService productService;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ProductViewLogRepository productViewLogRepository;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@MockitoSpyBean
	private ProductViewLogSaver productViewLogSaver;

	private Product createProduct() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		return productRepository.save(ProductFixture.create(artist));
	}

	private Member createMember() {
		return memberRepository.save(MemberFixture.create("viewer-" + UUID.randomUUID() + "@groove.com"));
	}

	@Nested
	@DisplayName("getDetail()")
	class GetDetail {

		@Test
		@DisplayName("로그인 회원이 상품 상세를 조회하면 조회 로그가 저장되고 Redis 목록에 반영된다")
		void savesLogAndPushesToRedisWhenMemberViewsDetail() {
			// given
			Product product = createProduct();
			Member member = createMember();
			redisTemplate.delete(RecentViewRedisService.recentViewKey(member.getId()));

			// when
			productService.getDetail(product.getId(), member.getId());

			// then
			await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				List<ProductViewLog> logs = productViewLogRepository.findAll().stream()
						.filter(log -> log.getProduct().getId().equals(product.getId()))
						.filter(log -> log.getMember() != null && log.getMember().getId().equals(member.getId()))
						.toList();
				assertThat(logs).hasSize(1);

				List<String> recentViews = redisTemplate.opsForList()
						.range(RecentViewRedisService.recentViewKey(member.getId()), 0, -1);
				assertThat(recentViews).isNotNull();
				assertThat(recentViews.get(0)).isEqualTo(product.getId().toString());
			});

			redisTemplate.delete(RecentViewRedisService.recentViewKey(member.getId()));
		}

		@Test
		@DisplayName("비로그인 조회면 member 가 null 인 로그만 남고 Redis 키는 만들어지지 않는다")
		void savesAnonymousLogWithoutRedisKeyWhenMemberIdIsNull() {
			// given
			Product product = createProduct();

			// when
			productService.getDetail(product.getId(), null);

			// then
			await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				List<ProductViewLog> logs = productViewLogRepository.findAll().stream()
						.filter(log -> log.getProduct().getId().equals(product.getId()))
						.toList();
				assertThat(logs).hasSize(1);
				assertThat(logs.get(0).getMember()).isNull();
			});
			assertThat(redisTemplate.hasKey("recent-view:null")).isFalse();
		}

		@Test
		@DisplayName("조회 로그 저장이 실패해도 상품 상세 응답은 정상이다")
		void returnsDetailResponseEvenWhenLogSaveFails() {
			// given
			Product product = createProduct();
			Member member = createMember();
			redisTemplate.delete(RecentViewRedisService.recentViewKey(member.getId()));
			willThrow(new RuntimeException("boom")).given(productViewLogSaver).save(any());

			// when
			ProductDetailResponse response = productService.getDetail(product.getId(), member.getId());

			// then
			assertThat(response.id()).isEqualTo(product.getId());
			await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				List<String> recentViews = redisTemplate.opsForList()
						.range(RecentViewRedisService.recentViewKey(member.getId()), 0, -1);
				assertThat(recentViews).isNotNull();
				assertThat(recentViews.get(0)).isEqualTo(product.getId().toString());
			});

			redisTemplate.delete(RecentViewRedisService.recentViewKey(member.getId()));
		}
	}
}
