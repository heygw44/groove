package com.groove.auth.service;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.LoginLockProperties;
import com.groove.global.ratelimit.AttemptCounterRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 로그인 실패를 세어 연속 실패가 임계값을 넘으면 잠근다. 회원이 있으면 회원 단위, 없으면 정규화한 이메일 단위다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

	private static final String MEMBER_KEY_PREFIX = "login-fail:m:";
	private static final String EMAIL_KEY_PREFIX = "login-fail:e:";

	private final AttemptCounterRepository attemptCounterRepository;
	private final LoginLockProperties loginLockProperties;

	/**
	 * 같은 회원을 가리키는 이메일 표기 변형(대소문자·전각)이 카운터를 나눠 갖지 못하도록 회원이 있으면 id 로 잠근다.
	 * 회원이 없을 때만 NFKC 로 정규화한 이메일을 키로 쓴다.
	 */
	public static String lockKey(Long memberId, String email) {
		if (memberId != null) {
			return MEMBER_KEY_PREFIX + memberId;
		}
		return EMAIL_KEY_PREFIX + Normalizer.normalize(email.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
	}

	public void checkNotLocked(String lockKey) {
		try {
			if (attemptCounterRepository.count(lockKey) >= loginLockProperties.maxFailures()) {
				throw new BusinessException(ErrorCode.AUTH_LOGIN_LOCKED);
			}
		} catch (DataAccessException e) {
			// fail-open: Redis 장애로 로그인 자체를 막지 않는다. 키에 이메일이 들어갈 수 있어 로그에 남기지 않는다.
			log.warn("로그인 잠금 여부 확인 실패", e);
		}
	}

	public void recordFailure(String lockKey) {
		try {
			attemptCounterRepository.increment(lockKey, loginLockProperties.window());
		} catch (DataAccessException e) {
			log.warn("로그인 실패 기록 실패", e);
		}
	}

	public void reset(String lockKey) {
		try {
			attemptCounterRepository.delete(lockKey);
		} catch (DataAccessException e) {
			log.warn("로그인 실패 카운터 초기화 실패", e);
		}
	}
}
