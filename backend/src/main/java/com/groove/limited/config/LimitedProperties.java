package com.groove.limited.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "limited")
public record LimitedProperties(boolean redisEnabled) {
}
