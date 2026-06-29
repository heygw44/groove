package com.groove.catalog.album.application;

import com.groove.catalog.album.event.AlbumStockChangedEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문/결제 재고 변경(AlbumStockChangedEvent)을 AFTER_COMMIT 으로 받아 카탈로그 조회 캐시를 무효화한다.
 * 변경된 앨범의 상세 캐시는 per-id evict, 랜딩 목록 캐시(단일 키)는 비운다 — admin 쓰기 경로와 동일한 정합.
 * CacheConfig 가 캐시 advice 를 트랜잭션 바깥에 두고 이 리스너도 커밋 후 실행되므로, 트랜잭션 중 동시 read 의 stale 재적재 레이스가 없다.
 */
@Component
public class AlbumCacheEvictionOnStockChangedListener {

    private final CacheManager cacheManager;

    public AlbumCacheEvictionOnStockChangedListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(AlbumStockChangedEvent event) {
        Cache detail = cacheManager.getCache(AlbumCaches.DETAIL);
        if (detail != null) {
            event.albumIds().forEach(detail::evictIfPresent);
        }
        Cache landing = cacheManager.getCache(AlbumCaches.LANDING_LIST);
        if (landing != null) {
            landing.clear();
        }
    }
}
