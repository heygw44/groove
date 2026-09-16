package com.groove.global.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class IdempotentExecutorTest {

	private static final String SCOPE = "order";
	private static final Long MEMBER_ID = 1L;
	private static final String KEY = "11111111-1111-1111-1111-111111111111";
	private static final String REDIS_KEY = "idem:order:1:11111111-1111-1111-1111-111111111111";
	private static final Duration TTL = Duration.ofMinutes(10);

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private IdempotentExecutor idempotentExecutor;

	@BeforeEach
	void setUp() {
		idempotentExecutor = new IdempotentExecutor(redisTemplate, objectMapper);
	}

	@Nested
	@DisplayName("execute()")
	class Execute {

		@Test
		@DisplayName("키 선점에 성공하면 액션을 실행하고 완료 상태로 저장한다")
		void savesCompletedWhenAcquired() {
			// given
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).willReturn(true);
			TestResponse response = new TestResponse(1L, "ok");

			// when
			IdempotentResult<TestResponse> result = idempotentExecutor.execute(SCOPE, MEMBER_ID, KEY,
					new TestRequest("a"), TestResponse.class, () -> response);

			// then
			assertThat(result.response()).isEqualTo(response);
			assertThat(result.replayed()).isFalse();
			verify(valueOperations).set(eq(REDIS_KEY), contains("COMPLETED"), eq(TTL));
		}

		@Test
		@DisplayName("액션이 예외를 던지면 키를 삭제하고 원래 예외를 그대로 던진다")
		void deletesKeyAndRethrowsWhenActionFails() {
			// given
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).willReturn(true);
			RuntimeException actionException = new IllegalStateException("boom");

			// when & then
			assertThatThrownBy(() -> idempotentExecutor.execute(SCOPE, MEMBER_ID, KEY, new TestRequest("a"),
					TestResponse.class, () -> {
						throw actionException;
					}))
					.isSameAs(actionException);
			verify(redisTemplate).delete(REDIS_KEY);
			verify(valueOperations, never()).set(anyString(), anyString(), eq(TTL));
		}

		@Test
		@DisplayName("액션이 Error 를 던져도 키를 삭제하고 원래 Error 를 그대로 던진다")
		void deletesKeyAndRethrowsWhenActionThrowsError() {
			// given
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).willReturn(true);
			Error actionError = new Error("boom");

			// when & then
			assertThatThrownBy(() -> idempotentExecutor.execute(SCOPE, MEMBER_ID, KEY, new TestRequest("a"),
					TestResponse.class, () -> {
						throw actionError;
					}))
					.isSameAs(actionError);
			verify(redisTemplate).delete(REDIS_KEY);
			verify(valueOperations, never()).set(anyString(), anyString(), eq(TTL));
		}

		@Test
		@DisplayName("DEL 이 실패해도 원래 예외를 그대로 던진다")
		void keepsOriginalExceptionWhenDeleteFails() {
			// given
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).willReturn(true);
			willThrow(new RedisConnectionFailureException("connection refused"))
					.given(redisTemplate).delete(REDIS_KEY);
			RuntimeException actionException = new IllegalStateException("boom");

			// when & then
			assertThatThrownBy(() -> idempotentExecutor.execute(SCOPE, MEMBER_ID, KEY, new TestRequest("a"),
					TestResponse.class, () -> {
						throw actionException;
					}))
					.isSameAs(actionException);
		}

		@Test
		@DisplayName("PROCESSING 상태면 ORDER_REQUEST_IN_PROGRESS 예외를 던진다")
		void throwsInProgressWhenProcessing() {
			// given
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).willReturn(false);
			given(valueOperations.get(REDIS_KEY))
					.willReturn(processingJson(sha256(new TestRequest("a"))));

			// when & then
			assertThatThrownBy(() -> idempotentExecutor.execute(SCOPE, MEMBER_ID, KEY, new TestRequest("a"),
					TestResponse.class, () -> new TestResponse(1L, "ok")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.ORDER_REQUEST_IN_PROGRESS);
		}

		@Test
		@DisplayName("COMPLETED 상태면 저장된 응답을 replayed 로 반환한다")
		void returnsReplayedResponseWhenCompleted() {
			// given
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).willReturn(false);
			given(valueOperations.get(REDIS_KEY))
					.willReturn(completedJson(sha256(new TestRequest("a")), "{\"id\":1,\"value\":\"ok\"}"));

			// when
			IdempotentResult<TestResponse> result = idempotentExecutor.execute(SCOPE, MEMBER_ID, KEY,
					new TestRequest("a"), TestResponse.class, () -> new TestResponse(99L, "should-not-run"));

			// then
			assertThat(result.replayed()).isTrue();
			assertThat(result.response()).isEqualTo(new TestResponse(1L, "ok"));
		}

		@Test
		@DisplayName("저장된 요청 해시가 다르면 상태보다 먼저 IDEMPOTENCY_KEY_REUSED 예외를 던진다")
		void throwsReusedWhenHashDiffers() {
			// given
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).willReturn(false);
			given(valueOperations.get(REDIS_KEY)).willReturn(processingJson("다른-해시"));

			// when & then
			assertThatThrownBy(() -> idempotentExecutor.execute(SCOPE, MEMBER_ID, KEY, new TestRequest("a"),
					TestResponse.class, () -> new TestResponse(1L, "ok")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
		}

		@Test
		@DisplayName("값이 그 사이 사라졌으면 한 번만 재시도하고 또 사라지면 ORDER_REQUEST_IN_PROGRESS 예외를 던진다")
		void retriesOnceThenThrowsWhenValueMissing() {
			// given
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).willReturn(false);
			given(valueOperations.get(REDIS_KEY)).willReturn(null);

			// when & then
			assertThatThrownBy(() -> idempotentExecutor.execute(SCOPE, MEMBER_ID, KEY, new TestRequest("a"),
					TestResponse.class, () -> new TestResponse(1L, "ok")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.ORDER_REQUEST_IN_PROGRESS);
			verify(valueOperations, times(2)).setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL));
		}

		@Test
		@DisplayName("키 조회에 실패하면 액션을 실행하지 않고 진행 중으로 취급해 ORDER_REQUEST_IN_PROGRESS 예외를 던진다")
		void throwsInProgressWithoutRunningActionWhenGetFails() {
			// given
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			given(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).willReturn(false);
			willThrow(new RedisConnectionFailureException("connection refused"))
					.given(valueOperations).get(REDIS_KEY);
			AtomicBoolean actionCalled = new AtomicBoolean(false);

			// when & then
			assertThatThrownBy(() -> idempotentExecutor.execute(SCOPE, MEMBER_ID, KEY, new TestRequest("a"),
					TestResponse.class, () -> {
						actionCalled.set(true);
						return new TestResponse(1L, "ok");
					}))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.ORDER_REQUEST_IN_PROGRESS);
			assertThat(actionCalled).isFalse();
		}

		@Test
		@DisplayName("Redis 예외가 나면 멱등 없이 액션만 실행한다")
		void runsActionOnlyWhenRedisFails() {
			// given
			given(redisTemplate.opsForValue()).willReturn(valueOperations);
			willThrow(new RedisConnectionFailureException("connection refused"))
					.given(valueOperations).setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL));
			TestResponse response = new TestResponse(1L, "ok");

			// when
			IdempotentResult<TestResponse> result = idempotentExecutor.execute(SCOPE, MEMBER_ID, KEY,
					new TestRequest("a"), TestResponse.class, () -> response);

			// then
			assertThat(result.response()).isEqualTo(response);
			assertThat(result.replayed()).isFalse();
		}
	}

	private String processingJson(String requestHash) {
		return "{\"status\":\"PROCESSING\",\"requestHash\":\"" + requestHash + "\"}";
	}

	private String completedJson(String requestHash, String responseJson) {
		return "{\"status\":\"COMPLETED\",\"requestHash\":\"" + requestHash + "\",\"response\":" + responseJson + "}";
	}

	private String sha256(Object request) {
		try {
			byte[] serialized = objectMapper.writeValueAsBytes(request);
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(serialized));
		} catch (JsonProcessingException | NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private record TestRequest(String field) {
	}

	private record TestResponse(Long id, String value) {
	}
}
