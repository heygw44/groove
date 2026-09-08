package com.groove.recommend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 추천 상품 특성 캐시 TTL. 0 이하면 캐시를 끄고 매 요청마다 다시 읽는다. */
@ConfigurationProperties(prefix = "groove.recommend")
public record RecommendProperties(@DefaultValue("60s") Duration featureCacheTtl) {
}
