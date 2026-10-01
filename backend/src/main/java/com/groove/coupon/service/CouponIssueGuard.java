package com.groove.coupon.service;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.CouponIssueLockProperties;
import com.groove.global.ratelimit.AttemptCounterRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 없는 쿠폰 코드를 반복 입력하는 대입 시도를 회원 단위로 세어 막는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueGuard {

	private static final String KEY_PREFIX = "coupon-issue-fail:";

	private final AttemptCounterRepository attemptCounterRepository;
	private final CouponIssueLockProperties couponIssueLockProperties;

	public void checkNotLocked(Long memberId) {
		try {
			if (attemptCounterRepository.count(key(memberId)) >= couponIssueLockProperties.maxFailures()) {
				throw new BusinessException(ErrorCode.COUPON_ISSUE_LOCKED);
			}
		} catch (DataAccessException e) {
			// fail-open: Redis 장애로 쿠폰 발급 자체를 막지 않는다.
			log.warn("쿠폰 발급 잠금 여부 확인 실패", e);
		}
	}

	public void recordFailure(Long memberId) {
		try {
			attemptCounterRepository.increment(key(memberId), couponIssueLockProperties.window());
		} catch (DataAccessException e) {
			log.warn("쿠폰 코드 실패 기록 실패", e);
		}
	}

	private static String key(Long memberId) {
		return KEY_PREFIX + memberId;
	}
}
