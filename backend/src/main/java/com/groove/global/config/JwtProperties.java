package com.groove.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.groove.member.entity.MemberRole;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTokenExpiry, Duration refreshTokenExpiry,
		@DefaultValue("10s") Duration refreshTokenGrace, @DefaultValue("5m") Duration adminAccessTokenExpiry) {

	public Duration accessTokenExpiry(MemberRole role) {
		return role == MemberRole.ADMIN ? adminAccessTokenExpiry : accessTokenExpiry;
	}

	/** 회수 기록 TTL 처럼 어느 역할의 토큰이든 덮어야 할 때 쓰는 가장 긴 access 만료. */
	public Duration maxAccessTokenExpiry() {
		return accessTokenExpiry.compareTo(adminAccessTokenExpiry) >= 0 ? accessTokenExpiry : adminAccessTokenExpiry;
	}
}
