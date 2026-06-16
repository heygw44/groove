package com.groove.catalog.album.application;

import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;

/**
 * 앨범 공개 검색 조건. 모든 필드 nullable — null 항목은 AlbumSpecs 에서 noop predicate 로 처리된다.
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
     * 공개 기본 랜딩 조건 여부 — 모든 필터가 비어 있고 status 가 SELLING 인 상태.
     */
    public boolean isPublicLanding() {
        return keyword == null && artistId == null && genreId == null && labelId == null
                && minPrice == null && maxPrice == null && minYear == null && maxYear == null
                && format == null && limited == null && status == AlbumStatus.SELLING;
    }
}
