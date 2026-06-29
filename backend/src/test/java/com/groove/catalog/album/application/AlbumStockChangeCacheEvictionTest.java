package com.groove.catalog.album.application;

import com.groove.catalog.album.event.AlbumStockChangedEvent;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 주문/결제 재고 변경(AlbumStockChangedEvent)이 커밋 후 조회 캐시를 무효화하는지 가드(#369).
// 변경된 앨범 상세만 per-id evict, 랜딩 목록은 전체 clear, 트랜잭션 없이 발행되면 evict 안 됨(AFTER_COMMIT).
@SpringBootTest(properties = "spring.cache.type=caffeine")
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("재고 변경 조회 캐시 무효화 (#369)")
class AlbumStockChangeCacheEvictionTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        cacheManager.getCacheNames()
                .forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    @Test
    @DisplayName("커밋 후 — 변경된 앨범 상세만 evict, 랜딩 목록은 전체 clear")
    void afterCommit_evictsChangedDetailAndClearsLanding() {
        Cache detail = detailCache();
        Cache landing = landingCache();
        detail.put(1L, "album-1");
        detail.put(2L, "album-2");
        landing.put("album-public-landing", "landing-page");

        txTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new AlbumStockChangedEvent(Set.of(1L))));

        assertThat(detail.get(1L)).as("재고 변경된 앨범 상세는 evict 돼야 한다").isNull();
        assertThat(detail.get(2L)).as("변경되지 않은 앨범 상세는 유지돼야 한다").isNotNull();
        assertThat(landing.get("album-public-landing")).as("랜딩 목록은 전체 clear 돼야 한다").isNull();
    }

    @Test
    @DisplayName("트랜잭션 없이 발행 — evict 안 됨(AFTER_COMMIT 보장)")
    void withoutTransaction_doesNotEvict() {
        Cache detail = detailCache();
        Cache landing = landingCache();
        detail.put(1L, "album-1");
        landing.put("album-public-landing", "landing-page");

        eventPublisher.publishEvent(new AlbumStockChangedEvent(Set.of(1L)));

        assertThat(detail.get(1L))
                .as("커밋이 없으면 AFTER_COMMIT 리스너가 실행되지 않아 캐시가 유지돼야 한다")
                .isNotNull();
        assertThat(landing.get("album-public-landing"))
                .as("커밋이 없으면 랜딩 목록 캐시도 유지돼야 한다")
                .isNotNull();
    }

    private Cache detailCache() {
        return Objects.requireNonNull(cacheManager.getCache(AlbumCaches.DETAIL));
    }

    private Cache landingCache() {
        return Objects.requireNonNull(cacheManager.getCache(AlbumCaches.LANDING_LIST));
    }
}
