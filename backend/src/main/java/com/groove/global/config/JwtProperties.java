package com.groove.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTokenExpiry, Duration refreshTokenExpiry,
		@DefaultValue("10s") Duration refreshTokenGrace) {
}
