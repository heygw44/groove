package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RecentViewRedisServiceTest {

	@Mock
	StringRedisTemplate redisTemplate;

	@Mock
	RedisScript<Long> recentViewPushScript;

	@Mock
	ListOperations<String, String> listOperations;

	RecentViewRedisService recentViewRedisService;

	@Nested
	@DisplayName("push()")
	class Push {

		@Test
		@DisplayName("스크립트를 올바른 키·인자로 호출한다")
		void executesScriptWithKeyAndArgs() {
			// given
			recentViewRedisService = new RecentViewRedisService(redisTemplate, recentViewPushScript);

			// when
			recentViewRedisService.push(1L, 10L);

			// then
			verify(redisTemplate).execute(eq(recentViewPushScript),
					eq(List.of(RecentViewRedisService.recentViewKey(1L))), eq("10"), eq("20"),
					eq(String.valueOf(RecentViewRedisService.TTL.toSeconds())));
		}

		@Test
		@DisplayName("Redis 장애가 나도 예외를 던지지 않는다")
		void doesNotThrowWhenRedisFails() {
			// given
			recentViewRedisService = new RecentViewRedisService(redisTemplate, recentViewPushScript);
			willThrow(new QueryTimeoutException("timeout")).given(redisTemplate)
					.execute(eq(recentViewPushScript), eq(List.of(RecentViewRedisService.recentViewKey(1L))),
							eq("10"), eq("20"), eq(String.valueOf(RecentViewRedisService.TTL.toSeconds())));

			// when & then
			assertThatCode(() -> recentViewRedisService.push(1L, 10L)).doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("findRecentProductIds()")
	class FindRecentProductIds {

		@Test
		@DisplayName("LRANGE 결과를 Long 리스트로 매핑한다")
		void mapsRangeResultToLongList() {
			// given
			recentViewRedisService = new RecentViewRedisService(redisTemplate, recentViewPushScript);
			given(redisTemplate.opsForList()).willReturn(listOperations);
			given(listOperations.range(RecentViewRedisService.recentViewKey(1L), 0,
					RecentViewRedisService.MAX_SIZE - 1)).willReturn(List.of("10", "20", "30"));

			// when
			List<Long> result = recentViewRedisService.findRecentProductIds(1L);

			// then
			assertThat(result).containsExactly(10L, 20L, 30L);
		}

		@Test
		@DisplayName("DataAccessException 이 나면 빈 리스트를 반환한다")
		void returnsEmptyListWhenRedisFails() {
			// given
			recentViewRedisService = new RecentViewRedisService(redisTemplate, recentViewPushScript);
			given(redisTemplate.opsForList()).willThrow(new QueryTimeoutException("timeout"));

			// when
			List<Long> result = recentViewRedisService.findRecentProductIds(1L);

			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("값이 숫자로 파싱되지 않으면 빈 리스트를 반환한다")
		void returnsEmptyListWhenValueIsNotParsable() {
			// given
			recentViewRedisService = new RecentViewRedisService(redisTemplate, recentViewPushScript);
			given(redisTemplate.opsForList()).willReturn(listOperations);
			given(listOperations.range(RecentViewRedisService.recentViewKey(1L), 0,
					RecentViewRedisService.MAX_SIZE - 1)).willReturn(List.of("not-a-number"));

			// when
			List<Long> result = recentViewRedisService.findRecentProductIds(1L);

			// then
			assertThat(result).isEmpty();
		}
	}
}
