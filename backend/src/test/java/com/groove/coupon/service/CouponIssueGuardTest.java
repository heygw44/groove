package com.groove.coupon.service;

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
import com.groove.global.config.CouponIssueLockProperties;
import com.groove.support.IntegrationTestSupport;

/** 쿠폰 코드 실패 잠금(Redis)을 검증한다. */
class CouponIssueGuardTest extends IntegrationTestSupport {

	private static final AtomicLong ID_SEQUENCE = new AtomicLong(9_000_000_000L);

	@Autowired
	private CouponIssueGuard couponIssueGuard;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private CouponIssueLockProperties couponIssueLockProperties;

	@Nested
	@DisplayName("checkNotLocked()")
	class CheckNotLocked {

		@Test
		@DisplayName("실패 횟수가 임계값에 못 미치면 통과시키고 도달하면 잠근다")
		void locksOnlyAfterMaxFailures() {
			// given
			Long memberId = ID_SEQUENCE.incrementAndGet();
			for (int i = 0; i < couponIssueLockProperties.maxFailures() - 1; i++) {
				couponIssueGuard.recordFailure(memberId);
			}

			// when & then
			assertThatCode(() -> couponIssueGuard.checkNotLocked(memberId)).doesNotThrowAnyException();

			// when
			couponIssueGuard.recordFailure(memberId);

			// then
			assertThatThrownBy(() -> couponIssueGuard.checkNotLocked(memberId))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_ISSUE_LOCKED);
		}

		@Test
		@DisplayName("Redis 가 응답하지 않으면 예외를 던지지 않고 통과시킨다")
		void failsOpenWhenRedisIsDown() {
			// given
			Long memberId = ID_SEQUENCE.incrementAndGet();
			pauseRedis();

			// when & then
			try {
				assertThatCode(() -> couponIssueGuard.checkNotLocked(memberId)).doesNotThrowAnyException();
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
			couponIssueGuard.recordFailure(memberId);

			// then
			Long ttlMillis = redisTemplate.getExpire("coupon-issue-fail:" + memberId, TimeUnit.MILLISECONDS);
			assertThat(ttlMillis).isBetween(couponIssueLockProperties.window().minusSeconds(5).toMillis(),
					couponIssueLockProperties.window().toMillis());
		}

		@Test
		@DisplayName("Redis 가 응답하지 않으면 예외를 던지지 않는다")
		void failsOpenWhenRedisIsDown() {
			// given
			Long memberId = ID_SEQUENCE.incrementAndGet();
			pauseRedis();

			// when & then
			try {
				assertThatCode(() -> couponIssueGuard.recordFailure(memberId)).doesNotThrowAnyException();
			} finally {
				unpauseRedis();
			}
		}
	}
}
