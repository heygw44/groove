package com.groove.auth.jwt;

import java.util.OptionalLong;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.groove.auth.repository.AccessTokenRevocationRepository;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** access token 이 발급된 뒤 회원 세션이 통째로 폐기됐는지 확인한다(jti 블랙리스트 대신 회원 단위 시각 비교). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessTokenRevocationChecker {

	private final AccessTokenRevocationRepository accessTokenRevocationRepository;

	public void check(TokenClaims claims) {
		try {
			OptionalLong revokedAt = accessTokenRevocationRepository.findRevokedAt(claims.memberId());
			// 같은 초는 통과시킨다. 폐기 직후 재로그인해 같은 초에 발급된 토큰까지 막지 않기 위해서다.
			if (revokedAt.isPresent() && claims.issuedAt() < revokedAt.getAsLong()) {
				throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
			}
		} catch (DataAccessException | NumberFormatException e) {
			// fail-open: Redis 장애로 인증 전체를 막지 않는다. 토큰은 어차피 최대 accessTokenExpiry 후 자연 만료된다.
			log.warn("access token 폐기 여부 확인 실패 memberId={}", claims.memberId(), e);
		}
	}
}
