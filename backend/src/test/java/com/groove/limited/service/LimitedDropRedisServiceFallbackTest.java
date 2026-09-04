package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Redis 장애·값 파싱 실패 시 DB 폴백을 위해 예외를 삼키고 empty 를 돌려주는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class LimitedDropRedisServiceFallbackTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private LimitedDropRedisService limitedDropRedisService;

	@Nested
	@DisplayName("getStock()")
	class GetStock {

		@Test
		@DisplayName("Redis 연결 실패면 empty 를 반환한다")
		void returnsEmptyWhenRedisConnectionFails() {
			// given
			limitedDropRedisService = new LimitedDropRedisService(redisTemplate);
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			willThrow(new RedisConnectionFailureException("connection refused"))
					.given(valueOperations).get("limited:stock:1");

			// when
			Optional<Integer> result = limitedDropRedisService.getStock(1L);

			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("값이 숫자가 아니면 empty 를 반환한다")
		void returnsEmptyWhenValueIsNotNumeric() {
			// given
			limitedDropRedisService = new LimitedDropRedisService(redisTemplate);
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.get("limited:stock:1")).willReturn("not-a-number");

			// when
			Optional<Integer> result = limitedDropRedisService.getStock(1L);

			// then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("getStocks()")
	class GetStocks {

		@Test
		@DisplayName("Redis 연결 실패면 빈 맵을 반환한다")
		void returnsEmptyMapWhenRedisConnectionFails() {
			// given
			limitedDropRedisService = new LimitedDropRedisService(redisTemplate);
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			willThrow(new RedisConnectionFailureException("connection refused"))
					.given(valueOperations).multiGet(List.of("limited:stock:1", "limited:stock:2"));

			// when
			Map<Long, Integer> result = limitedDropRedisService.getStocks(List.of(1L, 2L));

			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("빈 입력이면 Redis 를 호출하지 않고 빈 맵을 반환한다")
		void returnsEmptyMapWithoutCallingRedisWhenInputEmpty() {
			// given
			limitedDropRedisService = new LimitedDropRedisService(redisTemplate);

			// when
			Map<Long, Integer> result = limitedDropRedisService.getStocks(List.of());

			// then
			assertThat(result).isEmpty();
		}
	}
}
