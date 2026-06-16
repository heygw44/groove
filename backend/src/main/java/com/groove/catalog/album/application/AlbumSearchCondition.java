package com.groove.catalog.album.application;

import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;

/**
 * 앨범 공개 검색 조건 (#34, API §3.3).
 *
 * <p>컨트롤러 요청 DTO 와 분리해 application 레이어가 web 의존을 갖지 않도록 한다.
 * 모든 필드 nullable — null 인 항목은 {@link com.groove.catalog.album.domain.AlbumSpecs}
 * 에서 noop predicate 로 처리되어 동적 조합된다.
 *
 * <p>{@code status} 가 null 이면 호출 측 (Public 컨트롤러) 이
 * {@link AlbumStatus#SELLING} 을 강제한다 — Admin 검색 경로(W6 외) 도입 시 status 를
 * 명시 지정하면 그대로 사용된다.
 */
public record AlbumSearchCondition(
        String keyword,
        Long artistId,
        Long genreId,
        Long labelId,
        Long minPrice,
        Long maxPrice,
        Integer minYear,
        Integer maxYear,
        AlbumFormat format,
        Boolean limited,
        AlbumStatus status
) {

    public AlbumSearchCondition withArtistId(Long newArtistId) {
        return new AlbumSearchCondition(
                keyword, newArtistId, genreId, labelId, minPrice, maxPrice,
                minYear, maxYear, format, limited, status);
    }

    /**
     * 공개 기본 랜딩 조건 여부 — 모든 필터가 비어 있고 status 가 {@link AlbumStatus#SELLING} 으로 강제된
     * 상태. 랜딩 목록 캐시 가드({@link AlbumCaches#isLandingRequest})에서 사용한다 — 이 경우의 첫 페이지만
     * 캐시되고, 필터가 하나라도 있으면 캐시를 우회한다.
     */
    public boolean isPublicLanding() {
        return keyword == null && artistId == null && genreId == null && labelId == null
                && minPrice == null && maxPrice == null && minYear == null && maxYear == null
                && format == null && limited == null && status == AlbumStatus.SELLING;
    }
}
