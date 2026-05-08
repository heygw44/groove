package com.groove.catalog.album.api.dto;

import com.groove.catalog.album.application.AlbumSearchCondition;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 앨범 공개 검색 요청 (API §3.3 GET /albums query 파라미터).
 *
 * <p>{@code @ModelAttribute} 바인딩 — record 컴포넌트가 setter-free 하지만 Spring 6 의 record
 * 데이터 바인더가 생성자 인자 매칭으로 채운다. 모든 필드 nullable.
 *
 * <p>{@code status} 는 컨트롤러에서 Public 경계를 강제 — null/SELLING/SOLD_OUT 만 허용,
 * HIDDEN 요청은 거부 (관리자 전용 카테고리). validation 은 컨트롤러에서 수행.
 *
 * <p>{@code keyword} 는 길이 제한 (DB 컬럼 길이 + LIKE 패턴 비용 보호). minPrice/maxPrice 는
 * 음수 거부, minYear/maxYear 는 short 범위 보호.
 */
public record AlbumSearchRequest(
        @Size(max = 200) String keyword,
        @Positive Long artistId,
        @Positive Long genreId,
        @Min(0) Long minPrice,
        @Min(0) Long maxPrice,
        @Min(1900) @Max(Short.MAX_VALUE) Integer minYear,
        @Min(1900) @Max(Short.MAX_VALUE) Integer maxYear,
        AlbumFormat format,
        Boolean isLimited,
        AlbumStatus status
) {

    /**
     * Public 경계용 변환 — status 가 null 이면 {@link AlbumStatus#SELLING} 으로 강제한다.
     * Admin 검색 경로(W6 외) 에서는 {@link #toCondition()} 을 사용해 status 를 그대로 전달한다.
     */
    public AlbumSearchCondition toPublicCondition() {
        AlbumStatus effective = (status == null) ? AlbumStatus.SELLING : status;
        return new AlbumSearchCondition(
                keyword, artistId, genreId, minPrice, maxPrice,
                minYear, maxYear, format, isLimited, effective);
    }
}
