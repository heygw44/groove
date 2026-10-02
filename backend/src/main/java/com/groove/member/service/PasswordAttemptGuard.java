package com.groove.member.service;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.PasswordLockProperties;
import com.groove.global.ratelimit.AttemptCounterRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 로그인 상태에서 하는 비밀번호 확인(변경·탈퇴)의 실패를 회원 단위로 세어 대입을 막는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordAttemptGuard {

	private static final String KEY_PREFIX = "password-fail:";

	private final AttemptCounterRepository attemptCounterRepository;
	private final PasswordLockProperties passwordLockProperties;

	public void checkNotLocked(Long memberId) {
		try {
			if (attemptCounterRepository.count(key(memberId)) >= passwordLockProperties.maxFailures()) {
				throw new BusinessException(ErrorCode.MEMBER_PASSWORD_LOCKED);
			}
		} catch (DataAccessException e) {
			// fail-open: Redis 장애로 비밀번호 변경·탈퇴 자체를 막지 않는다.
			log.warn("비밀번호 확인 잠금 여부 확인 실패", e);
		}
	}

	public void recordFailure(Long memberId) {
		try {
			attemptCounterRepository.increment(key(memberId), passwordLockProperties.window());
		} catch (DataAccessException e) {
			log.warn("비밀번호 확인 실패 기록 실패", e);
		}
	}

	public void reset(Long memberId) {
		try {
			attemptCounterRepository.delete(key(memberId));
		} catch (DataAccessException e) {
			log.warn("비밀번호 확인 실패 카운터 초기화 실패", e);
		}
	}

	private static String key(Long memberId) {
		return KEY_PREFIX + memberId;
	}
}
