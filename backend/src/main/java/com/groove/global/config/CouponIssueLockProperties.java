package com.groove.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 쿠폰 코드 대입 방어 임계값. 없는 코드를 maxFailures 번 입력하면 window 동안 발급을 막는다. */
@ConfigurationProperties(prefix = "groove.coupon.issue-lock")
public record CouponIssueLockProperties(@DefaultValue("10") int maxFailures, @DefaultValue("1h") Duration window) {
}
