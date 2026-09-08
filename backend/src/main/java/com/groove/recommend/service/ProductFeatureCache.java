package com.groove.recommend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.groove.recommend.config.RecommendProperties;
import com.groove.recommend.dto.ProductFeatureRow;
import com.groove.recommend.mapper.RecommendQueryMapper;

import lombok.RequiredArgsConstructor;

/**
 * {@link RecommendQueryMapper#findProductFeatures()} 결과를 TTL 동안 스냅샷으로 들고 있는 캐시.
 * 요청마다 상품 전체를 읽어 Java 객체로 바꾸는 비용이 추천 응답 시간의 대부분을 차지해 도입했다.
 */
@Component
@RequiredArgsConstructor
public class ProductFeatureCache {

	private final RecommendQueryMapper recommendQueryMapper;
	private final RecommendProperties recommendProperties;
	private final Clock clock;

	private final AtomicReference<Snapshot> snapshotRef = new AtomicReference<>();
	private final AtomicLong loadCount = new AtomicLong();

	public Map<Long, ProductFeature> get() {
		Snapshot snapshot = snapshotRef.get();
		if (isFresh(snapshot)) {
			return snapshot.features();
		}
		synchronized (this) {
			snapshot = snapshotRef.get();
			if (isFresh(snapshot)) {
				return snapshot.features();
			}
			snapshot = load();
			snapshotRef.set(snapshot);
			return snapshot.features();
		}
	}

	/** 테스트 단언용 적재 횟수. */
	public long loadCount() {
		return loadCount.get();
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void handle(ProductCatalogChangedEvent event) {
		snapshotRef.set(null);
	}

	private boolean isFresh(Snapshot snapshot) {
		if (snapshot == null) {
			return false;
		}
		Duration ttl = recommendProperties.featureCacheTtl();
		if (ttl.isZero() || ttl.isNegative()) {
			return false;
		}
		return snapshot.loadedAt().plus(ttl).isAfter(clock.instant());
	}

	private Snapshot load() {
		Map<Long, ProductFeature> features = new LinkedHashMap<>();
		for (ProductFeatureRow row : recommendQueryMapper.findProductFeatures()) {
			ProductFeature feature = ProductFeature.from(row);
			features.put(feature.id(), feature);
		}
		loadCount.incrementAndGet();
		return new Snapshot(Collections.unmodifiableMap(features), clock.instant());
	}

	private record Snapshot(Map<Long, ProductFeature> features, Instant loadedAt) {
	}
}
