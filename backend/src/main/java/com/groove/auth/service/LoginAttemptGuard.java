package com.groove.auth.service;

import java.util.Locale;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.groove.auth.repository.LoginAttemptRepository;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.LoginLockProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 이메일 단위 로그인 실패를 세어 연속 실패가 임계값을 넘으면 잠근다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

	private final LoginAttemptRepository loginAttemptRepository;
	private final LoginLockProperties loginLockProperties;

	public void checkNotLocked(String email) {
		try {
			if (loginAttemptRepository.count(key(email)) >= loginLockProperties.maxFailures()) {
				throw new BusinessException(ErrorCode.AUTH_LOGIN_LOCKED);
			}
		} catch (DataAccessException e) {
			// fail-open: Redis 장애로 로그인 자체를 막지 않는다. 이메일은 개인정보라 로그에 남기지 않는다.
			log.warn("로그인 잠금 여부 확인 실패", e);
		}
	}

	public void recordFailure(String email) {
		try {
			loginAttemptRepository.increment(key(email), loginLockProperties.window());
		} catch (DataAccessException e) {
			log.warn("로그인 실패 기록 실패", e);
		}
	}

	public void reset(String email) {
		try {
			loginAttemptRepository.delete(key(email));
		} catch (DataAccessException e) {
			log.warn("로그인 실패 카운터 초기화 실패", e);
		}
	}

	private static String key(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
