package com.groove.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.groove.member.entity.MemberRole;

/** 로그인 세션의 절대 만료 상한. 최초 로그인 시각부터 이 기간이 지나면 재발급도 거부한다. */
@ConfigurationProperties(prefix = "auth.session")
public record AuthSessionProperties(@DefaultValue("30d") Duration absoluteExpiry,
		@DefaultValue("12h") Duration adminAbsoluteExpiry) {

	public Duration absoluteExpiry(MemberRole role) {
		return role == MemberRole.ADMIN ? adminAbsoluteExpiry : absoluteExpiry;
	}
}
