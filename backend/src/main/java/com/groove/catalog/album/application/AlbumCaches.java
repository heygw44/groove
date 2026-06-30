package com.groove.catalog.album.application;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 앨범 조회 캐시 상수 + 랜딩 판별. 캐시 둘(DETAIL: 상세 단건, LANDING_LIST: 공개 기본 랜딩 목록)을 쓴다.
 * 단일 인스턴스는 in-process Caffeine, 멀티노드는 공유 Redis 로 노드 간 무효화를 일관시킨다(#366).
 */
public final class AlbumCaches {

    /** 앨범 상세 단건 캐시 이름. */
    public static final String DETAIL = "albumDetail";

    /** 공개 기본 랜딩 목록 캐시 이름. */
    public static final String LANDING_LIST = "albumLandingList";

    /** 랜딩 캐시 단일 키(SpEL 리터럴). */
    public static final String LANDING_KEY = "'album-public-landing'";

    /** isLandingRequest 를 @Cacheable condition 에서 호출하는 SpEL. */
    public static final String LANDING_CONDITION =
            "T(com.groove.catalog.album.application.AlbumCaches).isLandingRequest(#condition, #pageable)";

    /** 랜딩 기본 페이지 크기. */
    private static final int LANDING_PAGE_SIZE = 20;

    /** 랜딩 기본 정렬 — createdAt DESC. */
    private static final Sort LANDING_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private AlbumCaches() {
    }

    /**
     * 공개 기본 랜딩 요청 여부 — 필터 전무 + status=SELLING 이면서 기본 페이지(page 0, size 20, createdAt DESC)일 때만 true.
     */
    public static boolean isLandingRequest(AlbumSearchCondition condition, Pageable pageable) {
        return condition != null
                && condition.isPublicLanding()
                && pageable != null
                && pageable.getPageNumber() == 0
                && pageable.getPageSize() == LANDING_PAGE_SIZE
                && LANDING_SORT.equals(pageable.getSort());
    }
}
