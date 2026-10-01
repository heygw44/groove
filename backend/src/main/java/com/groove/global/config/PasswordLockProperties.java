package com.groove.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 비밀번호 확인(변경·탈퇴) 실패 잠금 임계값. 회원 단위로 maxFailures 번 틀리면 window 동안 막는다. */
@ConfigurationProperties(prefix = "groove.auth.password-lock")
public record PasswordLockProperties(@DefaultValue("5") int maxFailures, @DefaultValue("15m") Duration window) {
}
