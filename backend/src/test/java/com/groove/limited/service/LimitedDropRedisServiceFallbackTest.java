package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.limited.entity.LimitedAttemptResult;

/** Redis 장애·값 파싱 실패 시 DB 폴백을 위해 예외를 삼키고 empty 를 돌려주는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class LimitedDropRedisServiceFallbackTest {

	private static final List<String> RESERVE_KEYS = List.of("limited:stock:1", "limited:buyers:1",
			"limited:pending:1");

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private HashOperations<String, String, String> hashOperations;

	@Mock
	private AlertNotifier alertNotifier;

	private Clock clock;

	private LimitedDropRedisService limitedDropRedisService;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneId.of("Asia/Seoul"));
		limitedDropRedisService = new LimitedDropRedisService(redisTemplate, null, null, null, clock, alertNotifier);
	}

	@Nested
	@DisplayName("getStock()")
	class GetStock {

		@Test
		@DisplayName("Redis 연결 실패면 empty 를 반환한다")
		void returnsEmptyWhenRedisConnectionFails() {
			// given
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
			// when
			Map<Long, Integer> result = limitedDropRedisService.getStocks(List.of());

			// then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("reserve()")
	class Reserve {

		@Test
		@DisplayName("Lua 스크립트가 null 을 반환하면 예외를 던진다")
		void throwsWhenScriptReturnsNull() {
			// given
			given(redisTemplate.execute(any(), eq(RESERVE_KEYS), eq("10"), eq(String.valueOf(clock.millis()))))
					.willReturn(null);

			// when & then
			assertThatThrownBy(() -> limitedDropRedisService.reserve(1L, 10L))
					.isInstanceOf(IllegalStateException.class);
		}

		@Test
		@DisplayName("Lua 스크립트가 알 수 없는 코드를 반환하면 예외를 던진다")
		void throwsWhenScriptReturnsUnknownCode() {
			// given
			given(redisTemplate.execute(any(), eq(RESERVE_KEYS), eq("10"), eq(String.valueOf(clock.millis()))))
					.willReturn(99L);

			// when & then
			assertThatThrownBy(() -> limitedDropRedisService.reserve(1L, 10L))
					.isInstanceOf(IllegalStateException.class);
		}
	}

	@Nested
	@DisplayName("release()")
	class Release {

		@Test
		@DisplayName("정상 처리되면 Redis 복구 스크립트를 실행한다")
		void executesReleaseScriptWhenSucceeds() {
			// when
			limitedDropRedisService.release(1L, 10L);

			// then
			verify(redisTemplate).execute(any(), eq(RESERVE_KEYS), eq("10"));
		}

		@Test
		@DisplayName("Redis 장애가 나도 예외를 삼키고 로그만 남긴다")
		void swallowsRedisExceptionAndLogsOnly() {
			// given
			willThrow(new RedisConnectionFailureException("connection refused"))
					.given(redisTemplate)
					.execute(any(), eq(RESERVE_KEYS), eq("10"));

			// when & then
			assertThatCode(() -> limitedDropRedisService.release(1L, 10L)).doesNotThrowAnyException();
			verify(alertNotifier).notify(any(Alert.class));
		}
	}

	@Nested
	@DisplayName("findMissingStock()")
	class FindMissingStock {

		@Test
		@DisplayName("Redis 장애면 예외를 삼키지 않고 그대로 던진다")
		void propagatesRedisException() {
			// given
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			willThrow(new RedisConnectionFailureException("connection refused"))
					.given(valueOperations).multiGet(List.of("limited:stock:1"));

			// when & then
			assertThatThrownBy(() -> limitedDropRedisService.findMissingStock(List.of(1L)))
					.isInstanceOf(RedisConnectionFailureException.class);
		}
	}

	@Nested
	@DisplayName("recordAttempt()")
	class RecordAttempt {

		@Test
		@DisplayName("Redis 연결 실패면 예외를 삼키고 전파하지 않는다")
		void swallowsRedisException() {
			// given
			given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
			willThrow(new RedisConnectionFailureException("connection refused"))
					.given(hashOperations).increment("limited:attempts:1", "SOLD_OUT", 1L);

			// when & then
			assertThatCode(() -> limitedDropRedisService.recordAttempt(1L, LimitedAttemptResult.SOLD_OUT))
					.doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("getAttempts()")
	class GetAttempts {

		@Test
		@DisplayName("정상 조회하면 파싱된 집계를 반환한다")
		void returnsParsedAttemptsWhenSucceeds() {
			// given
			given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
			given(hashOperations.entries("limited:attempts:1"))
					.willReturn(Map.of("SOLD_OUT", "3", "CLOSED", "1"));

			// when
			Map<LimitedAttemptResult, Long> result = limitedDropRedisService.getAttempts(1L);

			// then
			assertThat(result)
					.containsEntry(LimitedAttemptResult.SOLD_OUT, 3L)
					.containsEntry(LimitedAttemptResult.CLOSED, 1L);
		}

		@Test
		@DisplayName("알 수 없는 필드나 숫자가 아닌 값은 무시하고 나머지만 파싱한다")
		void ignoresUnparsableFieldsAndValues() {
			// given
			given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
			given(hashOperations.entries("limited:attempts:1"))
					.willReturn(Map.of("UNKNOWN_FIELD", "3", "SOLD_OUT", "not-a-number", "CLOSED", "2"));

			// when
			Map<LimitedAttemptResult, Long> result = limitedDropRedisService.getAttempts(1L);

			// then
			assertThat(result).hasSize(1);
			assertThat(result).containsEntry(LimitedAttemptResult.CLOSED, 2L);
		}

		@Test
		@DisplayName("Redis 연결 실패면 빈 맵을 반환한다")
		void returnsEmptyMapWhenRedisConnectionFails() {
			// given
			given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
			willThrow(new RedisConnectionFailureException("connection refused"))
					.given(hashOperations).entries("limited:attempts:1");

			// when
			Map<LimitedAttemptResult, Long> result = limitedDropRedisService.getAttempts(1L);

			// then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("getAttempts(Collection)")
	class GetAttemptsBatch {

		@Test
		@DisplayName("Redis 연결 실패면 예외를 삼키고 빈 맵을 반환한다")
		void returnsEmptyMapWhenRedisConnectionFails() {
			// given
			given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
			willThrow(new RedisConnectionFailureException("connection refused"))
					.given(hashOperations).entries("limited:attempts:1");

			// when
			Map<Long, Map<LimitedAttemptResult, Long>> result = limitedDropRedisService.getAttempts(List.of(1L));

			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("드롭 id 목록이 비어 있으면 Redis 를 호출하지 않고 빈 맵을 반환한다")
		void returnsEmptyMapWithoutCallingRedisWhenNoDropIds() {
			// when
			Map<Long, Map<LimitedAttemptResult, Long>> result = limitedDropRedisService.getAttempts(List.of());

			// then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("getAttemptsForFlush()")
	class GetAttemptsForFlush {

		@Test
		@DisplayName("Redis 연결 실패면 예외를 삼키지 않고 그대로 던진다")
		void propagatesRedisException() {
			// given
			given(redisTemplate.<String, String>opsForHash()).willReturn(hashOperations);
			willThrow(new RedisConnectionFailureException("connection refused"))
					.given(hashOperations).entries("limited:attempts:1");

			// when & then
			assertThatThrownBy(() -> limitedDropRedisService.getAttemptsForFlush(1L))
					.isInstanceOf(RedisConnectionFailureException.class);
		}
	}
}
