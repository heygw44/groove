package com.groove.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 로그인 실패 잠금 임계값. 같은 이메일로 maxFailures 번 실패하면 window 동안 로그인을 막는다. */
@ConfigurationProperties(prefix = "groove.auth.login-lock")
public record LoginLockProperties(@DefaultValue("5") int maxFailures, @DefaultValue("15m") Duration window) {
}
