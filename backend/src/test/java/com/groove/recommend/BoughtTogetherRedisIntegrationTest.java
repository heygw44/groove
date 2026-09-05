package com.groove.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.recommend.service.BoughtTogetherRedisService;
import com.groove.support.IntegrationTestSupport;

class BoughtTogetherRedisIntegrationTest extends IntegrationTestSupport {

	private static final Long PRODUCT_A = 9_185_001L;
	private static final Long PRODUCT_B = 9_185_002L;
	private static final Long PRODUCT_C = 9_185_003L;
	private static final Long PRODUCT_NO_DATA = 9_185_099L;

	@Autowired
	private BoughtTogetherRedisService boughtTogetherRedisService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@BeforeEach
	@AfterEach
	void cleanUpKeys() {
		redisTemplate.delete(BoughtTogetherRedisService.boughtTogetherKey(PRODUCT_A));
		redisTemplate.delete(BoughtTogetherRedisService.boughtTogetherKey(PRODUCT_B));
		redisTemplate.delete(BoughtTogetherRedisService.boughtTogetherKey(PRODUCT_A) + ":tmp");
	}

	@Nested
	@DisplayName("replaceAll()")
	class ReplaceAll {

		@Test
		@DisplayName("적재하면 findScores 가 score 내림차순으로 반환하고 TTL 이 설정된다")
		void replacesAndSetsExpectedTtl() {
			// given
			Map<Long, Map<Long, Long>> counts = Map.of(PRODUCT_A, Map.of(PRODUCT_B, 3L, PRODUCT_C, 7L));

			// when
			boughtTogetherRedisService.replaceAll(counts);

			// then
			Map<Long, Double> scores = boughtTogetherRedisService.findScores(PRODUCT_A);
			assertThat(scores.keySet()).containsExactly(PRODUCT_C, PRODUCT_B);
			assertThat(scores.get(PRODUCT_C)).isEqualTo(7.0);
			assertThat(scores.get(PRODUCT_B)).isEqualTo(3.0);

			String key = BoughtTogetherRedisService.boughtTogetherKey(PRODUCT_A);
			Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
			assertThat(expire).isGreaterThan(0);
			assertThat(expire).isLessThanOrEqualTo(BoughtTogetherRedisService.TTL.toSeconds());
		}

		@Test
		@DisplayName("다시 적재하면 이전에 있던 상대 상품이 사라지고 임시 키도 남지 않는다")
		void atomicallyReplacesAndLeavesNoTmpKey() {
			// given
			boughtTogetherRedisService.replaceAll(Map.of(PRODUCT_A, Map.of(PRODUCT_B, 1L, PRODUCT_C, 1L)));

			// when
			boughtTogetherRedisService.replaceAll(Map.of(PRODUCT_A, Map.of(PRODUCT_B, 9L)));

			// then
			Map<Long, Double> scores = boughtTogetherRedisService.findScores(PRODUCT_A);
			assertThat(scores).containsOnlyKeys(PRODUCT_B);
			assertThat(scores.get(PRODUCT_B)).isEqualTo(9.0);
			assertThat(redisTemplate.hasKey(BoughtTogetherRedisService.boughtTogetherKey(PRODUCT_A) + ":tmp"))
					.isFalse();
		}
	}

	@Nested
	@DisplayName("findScores(Collection)")
	class FindScoresBatch {

		@Test
		@DisplayName("여러 상품과 데이터 없는 상품을 함께 처리한다")
		void handlesMultipleProductsIncludingEmptyOne() {
			// given
			boughtTogetherRedisService.replaceAll(Map.of(
					PRODUCT_A, Map.of(PRODUCT_B, 4L),
					PRODUCT_B, Map.of(PRODUCT_A, 4L)));

			// when
			Map<Long, Map<Long, Double>> result = boughtTogetherRedisService
					.findScores(List.of(PRODUCT_A, PRODUCT_B, PRODUCT_NO_DATA));

			// then
			assertThat(result.get(PRODUCT_A)).containsEntry(PRODUCT_B, 4.0);
			assertThat(result.get(PRODUCT_B)).containsEntry(PRODUCT_A, 4.0);
			assertThat(result.get(PRODUCT_NO_DATA)).isEmpty();
		}
	}
}
