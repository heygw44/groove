package com.groove.limited.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "limited")
public record LimitedProperties(boolean redisEnabled, @DefaultValue("3s") Duration metaCacheTtl) {
}
