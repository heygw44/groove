package com.groove.recommend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.groove.recommend.service.RecommendWeights;

/**
 * 추천 상품 특성 캐시 TTL 과 점수 가중치. {@code weights} 는 값 객체({@link RecommendWeights})만 물고
 * 있고, 실제 기본값·계산 로직은 그쪽에 있다 — jacoco 가 {@code config} 패키지를 커버리지에서 빼기 때문이다.
 */
@ConfigurationProperties(prefix = "groove.recommend")
public record RecommendProperties(@DefaultValue("60s") Duration featureCacheTtl, RecommendWeights weights) {
}
