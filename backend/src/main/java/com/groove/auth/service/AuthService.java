package com.groove.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.groove.auth.dto.AuthTokens;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.dto.SignupResponse;
import com.groove.auth.jwt.JwtProvider;
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
		return issueTokens(member);
	}

	public AuthTokens reissue(String refreshToken) {
		if (!StringUtils.hasText(refreshToken)) {
			throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
		}
		Long memberId = jwtProvider.parseRefreshToken(refreshToken);
		String savedToken = refreshTokenRepository.findByMemberId(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND));
		if (!savedToken.equals(refreshToken)) {
			// 이미 사용된 토큰의 재사용(탈취 의심)이므로 세션을 폐기한다.
			refreshTokenRepository.deleteByMemberId(memberId);
			throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_MISMATCH);
		}
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		member.validateActive();
		return issueTokens(member);
	}

	public void logout(Long memberId) {
		refreshTokenRepository.deleteByMemberId(memberId);
	}

	private AuthTokens issueTokens(Member member) {
		String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
		String refreshToken = jwtProvider.createRefreshToken(member.getId());
		refreshTokenRepository.save(member.getId(), refreshToken);
		return new AuthTokens(accessToken, refreshToken, jwtProperties.accessTokenExpiry().toSeconds());
	}
}
