package com.groove.recommend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link RecommendProperties} 바인딩 전용. */
@Configuration
@EnableConfigurationProperties(RecommendProperties.class)
public class RecommendConfig {
}
