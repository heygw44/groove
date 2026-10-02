package com.groove.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.groove.member.entity.MemberRole;

/** 로그인 세션의 절대 만료 상한과 관리자 유휴 만료. 최초 로그인부터 절대 만료가 지나면 재발급도 거부한다. */
@ConfigurationProperties(prefix = "auth.session")
public record AuthSessionProperties(@DefaultValue("30d") Duration absoluteExpiry,
		@DefaultValue("12h") Duration adminAbsoluteExpiry,
		@DefaultValue("20m") Duration adminIdleTimeout) {

	public Duration absoluteExpiry(MemberRole role) {
		return role == MemberRole.ADMIN ? adminAbsoluteExpiry : absoluteExpiry;
	}
}
