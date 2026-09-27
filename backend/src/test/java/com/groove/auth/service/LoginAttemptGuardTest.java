package com.groove.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.LoginLockProperties;
import com.groove.support.IntegrationTestSupport;

/** 이메일 단위 로그인 실패 잠금(Redis)을 검증한다. */
class LoginAttemptGuardTest extends IntegrationTestSupport {

	private static final String KEY_PREFIX = "login-fail:";

	@Autowired
	private LoginAttemptGuard loginAttemptGuard;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private LoginLockProperties loginLockProperties;

	@Nested
	@DisplayName("checkNotLocked()")
	class CheckNotLocked {

		@Test
		@DisplayName("실패 횟수가 임계값에 못 미치면 통과시키고 도달하면 잠근다")
		void locksOnlyAfterMaxFailures() {
			// given
			String email = newEmail();
			int maxFailures = loginLockProperties.maxFailures();
			for (int i = 0; i < maxFailures - 1; i++) {
				loginAttemptGuard.recordFailure(email);
			}

			// when & then
			assertThatCode(() -> loginAttemptGuard.checkNotLocked(email)).doesNotThrowAnyException();

			// when
			loginAttemptGuard.recordFailure(email);

			// then
			assertThatThrownBy(() -> loginAttemptGuard.checkNotLocked(email))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_LOGIN_LOCKED);
		}

		@Test
		@DisplayName("이메일의 대소문자와 앞뒤 공백이 달라도 같은 키로 취급한다")
		void treatsCaseAndWhitespaceVariantsAsSameKey() {
			// given
			String email = newEmail();
			int maxFailures = loginLockProperties.maxFailures();
			String variant = "  " + email.toUpperCase(Locale.ROOT) + "  ";

			// when
			for (int i = 0; i < maxFailures; i++) {
				loginAttemptGuard.recordFailure(variant);
			}

			// then
			assertThatThrownBy(() -> loginAttemptGuard.checkNotLocked(email))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_LOGIN_LOCKED);
		}

		@Test
		@DisplayName("Redis 가 응답하지 않으면 예외를 던지지 않고 통과시킨다")
		void failsOpenWhenRedisIsDown() {
			// given
			String email = newEmail();
			pauseRedis();

			// when & then
			try {
				assertThatCode(() -> loginAttemptGuard.checkNotLocked(email)).doesNotThrowAnyException();
			} finally {
				unpauseRedis();
			}
		}
	}

	@Nested
	@DisplayName("recordFailure()")
	class RecordFailure {

		@Test
		@DisplayName("실패를 기록하면 TTL 을 window 로 다시 건다")
		void setsTtlNearWindow() {
			// given
			String email = newEmail();
			Duration window = loginLockProperties.window();

			// when
			loginAttemptGuard.recordFailure(email);

			// then
			Long ttlMillis = redisTemplate.getExpire(key(email), TimeUnit.MILLISECONDS);
			assertThat(ttlMillis).isNotNull();
			assertThat(ttlMillis).isBetween(window.minusSeconds(5).toMillis(), window.toMillis());
		}

		@Test
		@DisplayName("Redis 가 응답하지 않으면 예외를 던지지 않는다")
		void failsOpenWhenRedisIsDown() {
			// given
			String email = newEmail();
			pauseRedis();

			// when & then
			try {
				assertThatCode(() -> loginAttemptGuard.recordFailure(email)).doesNotThrowAnyException();
			} finally {
				unpauseRedis();
			}
		}
	}

	@Nested
	@DisplayName("reset()")
	class Reset {

		@Test
		@DisplayName("초기화하면 카운터가 사라져 다시 잠기지 않는다")
		void clearsCounter() {
			// given
			String email = newEmail();
			int maxFailures = loginLockProperties.maxFailures();
			for (int i = 0; i < maxFailures; i++) {
				loginAttemptGuard.recordFailure(email);
			}

			// when
			loginAttemptGuard.reset(email);

			// then
			assertThatCode(() -> loginAttemptGuard.checkNotLocked(email)).doesNotThrowAnyException();
			assertThat(redisTemplate.hasKey(key(email))).isFalse();
		}

		@Test
		@DisplayName("Redis 가 응답하지 않으면 예외를 던지지 않는다")
		void failsOpenWhenRedisIsDown() {
			// given
			String email = newEmail();
			pauseRedis();

			// when & then
			try {
				assertThatCode(() -> loginAttemptGuard.reset(email)).doesNotThrowAnyException();
			} finally {
				unpauseRedis();
			}
		}
	}

	private static String newEmail() {
		return "guard-" + UUID.randomUUID() + "@groove.com";
	}

	private static String key(String email) {
		return KEY_PREFIX + email.trim().toLowerCase(Locale.ROOT);
	}
}
