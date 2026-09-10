package com.groove.recommend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.groove.recommend.service.RecommendWeights;

/** {@link RecommendProperties} 바인딩과 {@link RecommendWeights} 빈 등록. */
@Configuration
@EnableConfigurationProperties(RecommendProperties.class)
public class RecommendConfig {

	@Bean
	public RecommendWeights recommendWeights(RecommendProperties properties) {
		return properties.weights() == null ? RecommendWeights.DEFAULT : properties.weights();
	}
}
