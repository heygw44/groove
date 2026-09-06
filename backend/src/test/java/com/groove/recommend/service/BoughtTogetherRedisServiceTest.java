package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DefaultStringTuple;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

@ExtendWith(MockitoExtension.class)
class BoughtTogetherRedisServiceTest {

	@Mock
	StringRedisTemplate redisTemplate;

	@Mock
	StringRedisConnection stringRedisConnection;

	@Mock
	ZSetOperations<String, String> zSetOperations;

	BoughtTogetherRedisService boughtTogetherRedisService;

	@Nested
	@DisplayName("replaceAll()")
	class ReplaceAll {

		@Test
		@DisplayName("빈 맵이면 Redis 를 호출하지 않는다")
		void doesNotCallRedisWhenMapIsEmpty() {
			// given
			boughtTogetherRedisService = new BoughtTogetherRedisService(redisTemplate);

			// when
			boughtTogetherRedisService.replaceAll(Map.of());

			// then
			verify(redisTemplate, never()).executePipelined(any(RedisCallback.class));
		}

		@Test
		@DisplayName("Redis 장애가 나도 예외를 삼킨다")
		void swallowsRedisFailureOnReplace() {
			// given
			boughtTogetherRedisService = new BoughtTogetherRedisService(redisTemplate);
			willThrow(new RedisConnectionFailureException("down")).given(redisTemplate)
					.executePipelined(any(RedisCallback.class));

			// when & then
			assertThatCode(() -> boughtTogetherRedisService.replaceAll(Map.of(1L, Map.of(2L, 3L))))
					.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("상대 상품이 없는 항목은 건너뛰고 Redis 커넥션을 건드리지 않는다")
		void skipsEntryWithoutOtherProducts() {
			// given
			boughtTogetherRedisService = new BoughtTogetherRedisService(redisTemplate);
			stubExecutePipelinedToRunCallback();

			// when
			boughtTogetherRedisService.replaceAll(Map.of(1L, Map.of()));

			// then
			verifyNoInteractions(stringRedisConnection);
		}

		@Test
		@DisplayName("임시 키에 zAdd 후 rename, expire 로 스왑한다")
		void addsToTempKeyThenRenamesAndExpires() {
			// given
			boughtTogetherRedisService = new BoughtTogetherRedisService(redisTemplate);
			stubExecutePipelinedToRunCallback();
			String key = BoughtTogetherRedisService.boughtTogetherKey(1L);
			String tmpKey = key + ":tmp";

			// when
			boughtTogetherRedisService.replaceAll(Map.of(1L, Map.of(2L, 3L, 4L, 5L)));

			// then
			verify(stringRedisConnection).zAdd(eq(tmpKey), eq(3.0), eq("2"));
			verify(stringRedisConnection).zAdd(eq(tmpKey), eq(5.0), eq("4"));
			verify(stringRedisConnection).rename(eq(tmpKey), eq(key));
			verify(stringRedisConnection).expire(eq(key), eq(BoughtTogetherRedisService.TTL.toSeconds()));
		}

		private void stubExecutePipelinedToRunCallback() {
			willAnswer(invocation -> {
				RedisCallback<Object> callback = invocation.getArgument(0);
				callback.doInRedis(stringRedisConnection);
				return List.of();
			}).given(redisTemplate).executePipelined(any(RedisCallback.class));
		}
	}

	@Nested
	@DisplayName("findScores(Long)")
	class FindScoresSingle {

		@Test
		@DisplayName("score 내림차순을 유지하며 Long 맵으로 변환한다")
		void convertsToScoreMapKeepingDescendingOrder() {
			// given
			boughtTogetherRedisService = new BoughtTogetherRedisService(redisTemplate);
			Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
			tuples.add(new DefaultTypedTuple<>("3", 7.0));
			tuples.add(new DefaultTypedTuple<>("2", 3.0));
			given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
			given(zSetOperations.reverseRangeWithScores(BoughtTogetherRedisService.boughtTogetherKey(1L), 0, -1))
					.willReturn(tuples);

			// when
			Map<Long, Double> scores = boughtTogetherRedisService.findScores(1L);

			// then
			assertThat(scores.keySet()).containsExactly(3L, 2L);
			assertThat(scores.get(3L)).isEqualTo(7.0);
			assertThat(scores.get(2L)).isEqualTo(3.0);
		}

		@Test
		@DisplayName("DataAccessException 이 나면 빈 맵을 반환한다")
		void returnsEmptyMapWhenRedisFails() {
			// given
			boughtTogetherRedisService = new BoughtTogetherRedisService(redisTemplate);
			given(redisTemplate.opsForZSet()).willThrow(new RedisConnectionFailureException("down"));

			// when
			Map<Long, Double> scores = boughtTogetherRedisService.findScores(1L);

			// then
			assertThat(scores).isEmpty();
		}

		@Test
		@DisplayName("멤버 값이 숫자가 아니면 빈 맵을 반환한다")
		void returnsEmptyMapWhenMemberIsNotNumeric() {
			// given
			boughtTogetherRedisService = new BoughtTogetherRedisService(redisTemplate);
			given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
			given(zSetOperations.reverseRangeWithScores(BoughtTogetherRedisService.boughtTogetherKey(1L), 0, -1))
					.willReturn(Set.of(new DefaultTypedTuple<>("not-a-number", 1.0)));

			// when
			Map<Long, Double> scores = boughtTogetherRedisService.findScores(1L);

			// then
			assertThat(scores).isEmpty();
		}

		@Test
		@DisplayName("결과가 없으면 빈 맵을 반환한다")
		void returnsEmptyMapWhenResultIsNull() {
			// given
			boughtTogetherRedisService = new BoughtTogetherRedisService(redisTemplate);
			given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
			given(zSetOperations.reverseRangeWithScores(BoughtTogetherRedisService.boughtTogetherKey(1L), 0, -1))
					.willReturn(null);

			// when
			Map<Long, Double> scores = boughtTogetherRedisService.findScores(1L);

			// then
			assertThat(scores).isEmpty();
		}
	}

	@Nested
	@DisplayName("findScores(Collection)")
	class FindScoresBatch {

		@Test
		@DisplayName("입력이 비어 있으면 Redis 를 호출하지 않고 빈 맵을 반환한다")
		void returnsEmptyMapWithoutCallingRedisWhenInputIsEmpty() {
			// given
			boughtTogetherRedisService = new BoughtTogetherRedisService(redisTemplate);

			// when
			Map<Long, Map<Long, Double>> scores = boughtTogetherRedisService.findScores(List.of());

			// then
			assertThat(scores).isEmpty();
			verify(redisTemplate, never()).executePipelined(any(RedisCallback.class));
		}

		@Test
		@DisplayName("DataAccessException 이 나면 빈 맵을 반환한다")
		void returnsEmptyMapWhenRedisFails() {
			// given
			boughtTogetherRedisService = new BoughtTogetherRedisService(redisTemplate);
			willThrow(new RedisConnectionFailureException("down")).given(redisTemplate)
					.executePipelined(any(RedisCallback.class));

			// when
			Map<Long, Map<Long, Double>> scores = boughtTogetherRedisService.findScores(List.of(1L));

			// then
			assertThat(scores).isEmpty();
		}

		@Test
		@DisplayName("파이프라인 결과를 상품 순서대로 매핑하고 없는 상품은 빈 맵으로 채운다")
		void mapsPipelinedResultsInOrderFillingMissingWithEmptyMap() {
			// given
			boughtTogetherRedisService = new BoughtTogetherRedisService(redisTemplate);
			given(redisTemplate.executePipelined(any(RedisCallback.class)))
					.willReturn(List.of(
							Set.of(new DefaultStringTuple("2", 9.0)),
							Set.of()));

			// when
			Map<Long, Map<Long, Double>> scores = boughtTogetherRedisService.findScores(List.of(1L, 3L));

			// then
			assertThat(scores.get(1L)).containsEntry(2L, 9.0);
			assertThat(scores.get(3L)).isEmpty();
		}
	}
}
