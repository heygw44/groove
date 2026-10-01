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

/** 로그인 실패 잠금(Redis)을 검증한다. */
class LoginAttemptGuardTest extends IntegrationTestSupport {

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
			String key = LoginAttemptGuard.lockKey(null, email);
			int maxFailures = loginLockProperties.maxFailures();
			for (int i = 0; i < maxFailures - 1; i++) {
				loginAttemptGuard.recordFailure(key);
			}

			// when & then
			assertThatCode(() -> loginAttemptGuard.checkNotLocked(key)).doesNotThrowAnyException();

			// when
			loginAttemptGuard.recordFailure(key);

			// then
			assertThatThrownBy(() -> loginAttemptGuard.checkNotLocked(key))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_LOGIN_LOCKED);
		}

		@Test
		@DisplayName("회원이 없을 때 이메일의 대소문자·앞뒤 공백·전각 표기가 달라도 같은 키로 취급한다")
		void treatsEmailVariantsAsSameKeyForUnknownMember() {
			// given
			String email = newEmail();
			String key = LoginAttemptGuard.lockKey(null, email);
			int maxFailures = loginLockProperties.maxFailures();
			String variant = "  " + email.toUpperCase(Locale.ROOT) + "  ";
			String fullwidth = email.replace("guard", "ｇｕａｒｄ");
			String variantKey = LoginAttemptGuard.lockKey(null, variant);

			// when
			for (int i = 0; i < maxFailures; i++) {
				loginAttemptGuard.recordFailure(variantKey);
			}

			// then
			assertThat(LoginAttemptGuard.lockKey(null, fullwidth)).isEqualTo(key);
			assertThatThrownBy(() -> loginAttemptGuard.checkNotLocked(key))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_LOGIN_LOCKED);
		}

		@Test
		@DisplayName("회원이 있으면 이메일과 무관하게 회원 id 키를 쓴다")
		void usesMemberIdKeyWhenMemberExists() {
			// when & then
			assertThat(LoginAttemptGuard.lockKey(7L, "a@groove.com")).isEqualTo("login-fail:m:7");
			assertThat(LoginAttemptGuard.lockKey(7L, "ＡＢ@groove.com")).isEqualTo("login-fail:m:7");
		}

		@Test
		@DisplayName("Redis 가 응답하지 않으면 예외를 던지지 않고 통과시킨다")
		void failsOpenWhenRedisIsDown() {
			// given
			String email = newEmail();
			String key = LoginAttemptGuard.lockKey(null, email);
			pauseRedis();

			// when & then
			try {
				assertThatCode(() -> loginAttemptGuard.checkNotLocked(key)).doesNotThrowAnyException();
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
			String key = LoginAttemptGuard.lockKey(null, email);
			Duration window = loginLockProperties.window();

			// when
			loginAttemptGuard.recordFailure(key);

			// then
			Long ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
			assertThat(ttlMillis).isNotNull();
			assertThat(ttlMillis).isBetween(window.minusSeconds(5).toMillis(), window.toMillis());
		}

		@Test
		@DisplayName("Redis 가 응답하지 않으면 예외를 던지지 않는다")
		void failsOpenWhenRedisIsDown() {
			// given
			String email = newEmail();
			String key = LoginAttemptGuard.lockKey(null, email);
			pauseRedis();

			// when & then
			try {
				assertThatCode(() -> loginAttemptGuard.recordFailure(key)).doesNotThrowAnyException();
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
			String key = LoginAttemptGuard.lockKey(null, email);
			int maxFailures = loginLockProperties.maxFailures();
			for (int i = 0; i < maxFailures; i++) {
				loginAttemptGuard.recordFailure(key);
			}

			// when
			loginAttemptGuard.reset(key);

			// then
			assertThatCode(() -> loginAttemptGuard.checkNotLocked(key)).doesNotThrowAnyException();
			assertThat(redisTemplate.hasKey(key)).isFalse();
		}

		@Test
		@DisplayName("Redis 가 응답하지 않으면 예외를 던지지 않는다")
		void failsOpenWhenRedisIsDown() {
			// given
			String email = newEmail();
			String key = LoginAttemptGuard.lockKey(null, email);
			pauseRedis();

			// when & then
			try {
				assertThatCode(() -> loginAttemptGuard.reset(key)).doesNotThrowAnyException();
			} finally {
				unpauseRedis();
			}
		}
	}

	private static String newEmail() {
		return "guard-" + UUID.randomUUID() + "@groove.com";
	}

}
