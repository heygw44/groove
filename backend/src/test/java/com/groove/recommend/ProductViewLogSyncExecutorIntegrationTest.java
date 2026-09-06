package com.groove.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
import com.groove.recommend.service.ProductViewLogSaver;
import com.groove.recommend.service.RecentViewRedisService;
import com.groove.support.IntegrationTestSupport;

/**
 * viewLogExecutor 를 동기 실행기로 바꿔치기해 리스너를 요청 스레드에서 그대로 실행시킨다.
 * 리스너 예외가 삼켜지지 않으면 이 조건에서 getDetail() 이 즉시 터지므로, 응답 무영향을 가장 직접적으로 증명한다.
 */
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@Import(ProductViewLogSyncExecutorIntegrationTest.SyncExecutorConfig.class)
class ProductViewLogSyncExecutorIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ProductService productService;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MemberRepository memberRepository;

	@MockitoBean
	private ProductViewLogSaver productViewLogSaver;

	@MockitoBean
	private RecentViewRedisService recentViewRedisService;

	private Product createProduct() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		return productRepository.save(ProductFixture.create(artist));
	}

	private Member createMember() {
		return memberRepository.save(MemberFixture.create("sync-viewer-" + UUID.randomUUID() + "@groove.com"));
	}

	@Nested
	@DisplayName("getDetail()")
	class GetDetail {

		@Test
		@DisplayName("리스너의 DB 저장이 예외를 던져도 상품 상세 조회는 정상 응답한다")
		void returnsDetailResponseWhenDbSaveThrows() {
			// given
			Product product = createProduct();
			Member member = createMember();
			willThrow(new RuntimeException("db boom")).given(productViewLogSaver).save(any());
			AtomicReference<ProductDetailResponse> holder = new AtomicReference<>();

			// when & then
			assertThatCode(() -> holder.set(productService.getDetail(product.getId(), member.getId())))
					.doesNotThrowAnyException();
			assertThat(holder.get().id()).isEqualTo(product.getId());
		}

		@Test
		@DisplayName("리스너의 Redis 적재가 예외를 던져도 상품 상세 조회는 정상 응답한다")
		void returnsDetailResponseWhenRedisPushThrows() {
			// given
			Product product = createProduct();
			Member member = createMember();
			willThrow(new RuntimeException("redis boom")).given(recentViewRedisService).push(any(), any());
			AtomicReference<ProductDetailResponse> holder = new AtomicReference<>();

			// when & then
			assertThatCode(() -> holder.set(productService.getDetail(product.getId(), member.getId())))
					.doesNotThrowAnyException();
			assertThat(holder.get().id()).isEqualTo(product.getId());
		}

		@Test
		@DisplayName("DB 저장과 Redis 적재가 모두 예외를 던져도 상품 상세 조회는 정상 응답한다")
		void returnsDetailResponseWhenBothDbAndRedisThrow() {
			// given
			Product product = createProduct();
			Member member = createMember();
			willThrow(new RuntimeException("db boom")).given(productViewLogSaver).save(any());
			willThrow(new RuntimeException("redis boom")).given(recentViewRedisService).push(any(), any());
			AtomicReference<ProductDetailResponse> holder = new AtomicReference<>();

			// when & then
			assertThatCode(() -> holder.set(productService.getDetail(product.getId(), member.getId())))
					.doesNotThrowAnyException();
			assertThat(holder.get().id()).isEqualTo(product.getId());
		}
	}

	@TestConfiguration
	static class SyncExecutorConfig {

		@Bean
		public Executor viewLogExecutor() {
			return new SyncTaskExecutor();
		}
	}
}
