package com.groove.auth.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.groove.auth.dto.AuthTokens;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.dto.SignupResponse;
import com.groove.auth.jwt.JwtProvider;
import com.groove.auth.jwt.RefreshTokenClaims;
import com.groove.auth.repository.RefreshRotation;
import com.groove.auth.repository.RefreshTokenRepository;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.JwtProperties;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** 회원가입/로그인/토큰 재발급을 담당한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtProvider jwtProvider;
	private final JwtProperties jwtProperties;
	private final Clock clock;

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (memberRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.MEMBER_EMAIL_DUPLICATE);
		}
		Member member = Member.create(request.email(), passwordEncoder.encode(request.password()), request.nickname());
		return SignupResponse.from(memberRepository.save(member));
	}

	public AuthTokens login(LoginRequest request) {
		Member member = memberRepository.findByEmail(request.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
		if (!passwordEncoder.matches(request.password(), member.getPassword())) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}
		member.validateActive();
		String sessionId = UUID.randomUUID().toString();
		AuthTokens tokens = issueTokens(member, sessionId);
		refreshTokenRepository.save(member.getId(), sessionId, tokens.refreshToken());
		return tokens;
	}

	public AuthTokens reissue(String refreshToken) {
		if (!StringUtils.hasText(refreshToken)) {
			throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
		}
		RefreshTokenClaims claims = jwtProvider.parseRefreshToken(refreshToken);
		Long memberId = claims.memberId();
		String sessionId = claims.sessionId();
		String newToken = jwtProvider.createRefreshToken(memberId, sessionId);

		RefreshRotation rotation =
				refreshTokenRepository.rotate(memberId, sessionId, refreshToken, newToken, clock.millis());
		switch (rotation.result()) {
			case NOT_FOUND -> throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
			case REUSED -> throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_MISMATCH);
			default -> {
			}
		}

		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		member.validateActive();
		String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
		return new AuthTokens(accessToken, rotation.refreshToken(), jwtProperties.accessTokenExpiry().toSeconds());
	}

	public void logout(Long memberId, String refreshToken) {
		if (!StringUtils.hasText(refreshToken)) {
			return;
		}
		try {
			RefreshTokenClaims claims = jwtProvider.parseRefreshToken(refreshToken);
			if (claims.memberId().equals(memberId)) {
				refreshTokenRepository.delete(memberId, claims.sessionId());
			}
		} catch (BusinessException e) {
			// 만료·위조된 토큰의 세션은 TTL 로 이미 사라졌거나 애초에 없으므로 조용히 무시한다.
		}
	}

	private AuthTokens issueTokens(Member member, String sessionId) {
		String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
		String refreshToken = jwtProvider.createRefreshToken(member.getId(), sessionId);
		return new AuthTokens(accessToken, refreshToken, jwtProperties.accessTokenExpiry().toSeconds());
	}
}
