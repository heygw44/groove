package com.groove.auth.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.groove.auth.repository.AccessTokenRevocationRepository;
import com.groove.auth.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

/** 탈퇴·정지·비밀번호 변경 등 회원의 모든 로그인 세션을 강제로 끝낼 때 쓴다. */
@Service
@RequiredArgsConstructor
public class SessionRevoker {

	private final RefreshTokenRepository refreshTokenRepository;
	private final AccessTokenRevocationRepository accessTokenRevocationRepository;
	private final Clock clock;

	/** refresh token 을 지워 재발급을 막고, 지금 이 순간 이전에 발급된 access token 도 폐기 시각으로 막는다. */
	public void revokeAll(Long memberId) {
		refreshTokenRepository.deleteAllByMemberId(memberId);
		accessTokenRevocationRepository.markRevoked(memberId, clock.instant().getEpochSecond());
	}
}
