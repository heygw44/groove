package com.groove.catalog.album.application;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 앨범 조회 캐시 상수 + 랜딩 판별 (#236).
 *
 * <p>두 개의 Caffeine 캐시를 쓴다:
 * <ul>
 *   <li>{@link #DETAIL} — 앨범 상세 단건. key = albumId.
 *   <li>{@link #LANDING_LIST} — 공개 기본 랜딩 목록. 필터 없는 첫 페이지 한 가지만 캐시하므로
 *       {@link #LANDING_KEY} 단일 엔트리로 둔다(검색 조건 폭발·무효화 복잡도 회피).
 * </ul>
 *
 * <p>무효화 정책(#236): admin 쓰기(등록/수정/재고/삭제)는 즉시 evict, 주문 결제·재고 복원·리뷰 작성이
 * 바꾸는 stock·평점은 짧은 TTL(application.yaml {@code expireAfterWrite})로만 수렴시킨다 — catalog 밖
 * 도메인에 evict 훅을 박지 않아 결합을 최소화한다. 캐시는 표시용이고 결제 확정은 비관락
 * ({@code AlbumRepository#findByIdForUpdate})으로 재검증하므로 오버셀은 불가하다.
 */
public final class AlbumCaches {

    /** 앨범 상세 단건 캐시 이름 — {@code @Cacheable(key = "#id")}. */
    public static final String DETAIL = "albumDetail";

    /** 공개 기본 랜딩 목록 캐시 이름. */
    public static final String LANDING_LIST = "albumLandingList";

    /**
     * 랜딩 캐시 단일 키(SpEL 리터럴). {@link #isLandingRequest} 가드가 동일 쿼리(필터 전무 + 기본 페이지)를
     * 보장하므로 상수 키 하나로 충분하다.
     */
    public static final String LANDING_KEY = "'album-public-landing'";

    /** {@link #isLandingRequest} 를 {@code @Cacheable(condition = ...)} 에서 호출하는 SpEL. */
    public static final String LANDING_CONDITION =
            "T(com.groove.catalog.album.application.AlbumCaches).isLandingRequest(#condition, #pageable)";

    /** 컨트롤러 기본값과 일치해야 한다 — {@code @PageableDefault(size = 20)}. */
    private static final int LANDING_PAGE_SIZE = 20;

    /** 컨트롤러 기본값과 일치해야 한다 — {@code @SortDefault(sort = "createdAt", direction = DESC)}. */
    private static final Sort LANDING_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private AlbumCaches() {
    }

    /**
     * 공개 기본 랜딩 요청 여부 — 필터 전무 + status=SELLING({@link AlbumSearchCondition#isPublicLanding})
     * 이면서 기본 페이지(page 0, size 20, createdAt DESC)일 때만 {@code true}. 이 경우에만
     * {@link #LANDING_KEY} 단일 엔트리로 캐시된다. 필터 검색·비기본 정렬·admin 목록(status≠SELLING)은
     * 가드 불일치로 캐시를 우회한다.
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
