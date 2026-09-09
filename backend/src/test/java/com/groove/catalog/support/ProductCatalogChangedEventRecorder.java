package com.groove.catalog.support;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.groove.recommend.service.ProductCatalogChangedEvent;

/**
 * 통합 테스트에서 ProductCatalogChangedEvent 발행 횟수를 확인하기 위한 전역 리스너.
 * 다른 테스트가 같은 컨텍스트를 공유해 카운트가 누적되므로, 검증은 항상 호출 전후 델타로 한다.
 */
@Profile("test")
@Component
public class ProductCatalogChangedEventRecorder {

	private final AtomicInteger count = new AtomicInteger();

	@EventListener
	public void onEvent(ProductCatalogChangedEvent event) {
		count.incrementAndGet();
	}

	public int count() {
		return count.get();
	}
}
