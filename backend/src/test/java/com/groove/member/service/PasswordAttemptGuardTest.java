package com.groove.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.PasswordLockProperties;
import com.groove.support.IntegrationTestSupport;

/** 비밀번호 확인 실패 잠금(Redis)을 검증한다. */
class PasswordAttemptGuardTest extends IntegrationTestSupport {

	private static final AtomicLong ID_SEQUENCE = new AtomicLong(9_000_000_000L);

	@Autowired
	private PasswordAttemptGuard passwordAttemptGuard;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private PasswordLockProperties passwordLockProperties;

	@Nested
	@DisplayName("checkNotLocked()")
	class CheckNotLocked {

		@Test
		@DisplayName("실패 횟수가 임계값에 못 미치면 통과시키고 도달하면 잠근다")
		void locksOnlyAfterMaxFailures() {
			// given
			Long memberId = ID_SEQUENCE.incrementAndGet();
			for (int i = 0; i < passwordLockProperties.maxFailures() - 1; i++) {
				passwordAttemptGuard.recordFailure(memberId);
			}

			// when & then
			assertThatCode(() -> passwordAttemptGuard.checkNotLocked(memberId)).doesNotThrowAnyException();

			// when
			passwordAttemptGuard.recordFailure(memberId);

			// then
			assertThatThrownBy(() -> passwordAttemptGuard.checkNotLocked(memberId))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_PASSWORD_LOCKED);
		}

		@Test
		@DisplayName("Redis 가 응답하지 않으면 예외를 던지지 않고 통과시킨다")
		void failsOpenWhenRedisIsDown() {
			// given
			Long memberId = ID_SEQUENCE.incrementAndGet();
			pauseRedis();

			// when & then
			try {
				assertThatCode(() -> passwordAttemptGuard.checkNotLocked(memberId)).doesNotThrowAnyException();
			} finally {
				unpauseRedis();
			}
		}
	}

	@Nested
	@DisplayName("recordFailure()")
	class RecordFailure {

		@Test
		@DisplayName("실패를 기록하면 TTL 을 window 로 건다")
		void setsTtlNearWindow() {
			// given
			Long memberId = ID_SEQUENCE.incrementAndGet();

			// when
			passwordAttemptGuard.recordFailure(memberId);

			// then
			Long ttlMillis = redisTemplate.getExpire("password-fail:" + memberId, TimeUnit.MILLISECONDS);
			assertThat(ttlMillis).isBetween(passwordLockProperties.window().minusSeconds(5).toMillis(),
					passwordLockProperties.window().toMillis());
		}

		@Test
		@DisplayName("Redis 가 응답하지 않으면 예외를 던지지 않는다")
		void failsOpenWhenRedisIsDown() {
			// given
			Long memberId = ID_SEQUENCE.incrementAndGet();
			pauseRedis();

			// when & then
			try {
				assertThatCode(() -> passwordAttemptGuard.recordFailure(memberId)).doesNotThrowAnyException();
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
			Long memberId = ID_SEQUENCE.incrementAndGet();
			for (int i = 0; i < passwordLockProperties.maxFailures(); i++) {
				passwordAttemptGuard.recordFailure(memberId);
			}

			// when
			passwordAttemptGuard.reset(memberId);

			// then
			assertThatCode(() -> passwordAttemptGuard.checkNotLocked(memberId)).doesNotThrowAnyException();
			assertThat(redisTemplate.hasKey("password-fail:" + memberId)).isFalse();
		}
	}
}
