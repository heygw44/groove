package com.groove.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.JwtProperties;
import com.groove.member.entity.MemberRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Access/Refresh Token 발급 및 파싱. */
@Component
public class JwtProvider {

	private static final String CLAIM_ROLE = "role";
	private static final String CLAIM_TYPE = "typ";
	private static final String TYPE_ACCESS = "access";
	private static final String TYPE_REFRESH = "refresh";

	private final JwtProperties jwtProperties;
	private final SecretKey secretKey;

	public JwtProvider(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
		this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
	}

	public String createAccessToken(Long memberId, MemberRole role) {
		Date now = new Date();
		Date expiration = new Date(now.getTime() + jwtProperties.accessTokenExpiry().toMillis());
		return Jwts.builder()
				.subject(String.valueOf(memberId))
				.claim(CLAIM_ROLE, role.name())
				.claim(CLAIM_TYPE, TYPE_ACCESS)
				.issuedAt(now)
				.expiration(expiration)
				.signWith(secretKey, Jwts.SIG.HS256)
				.compact();
	}

	public String createRefreshToken(Long memberId) {
		Date now = new Date();
		Date expiration = new Date(now.getTime() + jwtProperties.refreshTokenExpiry().toMillis());
		return Jwts.builder()
				.subject(String.valueOf(memberId))
				.claim(CLAIM_TYPE, TYPE_REFRESH)
				.issuedAt(now)
				.expiration(expiration)
				.signWith(secretKey, Jwts.SIG.HS256)
				.compact();
	}

	public TokenClaims parseAccessToken(String token) {
		Claims claims = parse(token, TYPE_ACCESS);
		Long memberId = Long.valueOf(claims.getSubject());
		MemberRole role = MemberRole.valueOf(claims.get(CLAIM_ROLE, String.class));
		return new TokenClaims(memberId, role);
	}

	public Long parseRefreshToken(String token) {
		Claims claims = parse(token, TYPE_REFRESH);
		return Long.valueOf(claims.getSubject());
	}

	private Claims parse(String token, String expectedType) {
		Claims claims;
		try {
			claims = Jwts.parser()
					.verifyWith(secretKey)
					.build()
					.parseSignedClaims(token)
					.getPayload();
		} catch (ExpiredJwtException e) {
			throw new BusinessException(ErrorCode.AUTH_EXPIRED_TOKEN);
		} catch (JwtException | IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}
		return claims;
	}
}
