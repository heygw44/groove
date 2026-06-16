package com.groove.catalog.album.api.dto;

import com.groove.catalog.album.application.AlbumSearchCondition;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 앨범 검색 요청 (GET /albums query 파라미터). 모든 필드 nullable. */
public record AlbumSearchRequest(
        @Schema(description = "제목·설명 키워드 (최대 200자)", example = "memories")
        @Size(max = 200) String keyword,
        @Schema(description = "아티스트 ID 필터", example = "1")
        @Positive Long artistId,
        @Schema(description = "장르 ID 필터", example = "3")
        @Positive Long genreId,
        @Schema(description = "레이블 ID 필터", example = "7")
        @Positive Long labelId,
        @Schema(description = "최소 가격 (원)", example = "10000")
        @Min(0) Long minPrice,
        @Schema(description = "최대 가격 (원)", example = "50000")
        @Min(0) Long maxPrice,
        @Schema(description = "최소 발매 연도", example = "2000")
        @Min(1900) @Max(Short.MAX_VALUE) Integer minYear,
        @Schema(description = "최대 발매 연도", example = "2025")
        @Min(1900) @Max(Short.MAX_VALUE) Integer maxYear,
        @Schema(description = "음반 포맷 필터", example = "VINYL")
        AlbumFormat format,
        @Schema(description = "한정판 여부 필터", example = "true")
        Boolean isLimited,
        @Schema(description = "판매 상태 필터 (미지정 시 SELLING 강제, HIDDEN 은 공개 검색에서 거부)", example = "SELLING")
        AlbumStatus status
) {

    /** Public 변환 — status 가 null 이면 SELLING 으로 강제한다. */
    public AlbumSearchCondition toPublicCondition() {
        AlbumStatus effective = (status == null) ? AlbumStatus.SELLING : status;
        return new AlbumSearchCondition(
                keyword, artistId, genreId, labelId, minPrice, maxPrice,
                minYear, maxYear, format, isLimited, effective);
    }

    /** Admin 변환 — status 를 강제하지 않고 그대로 전달한다 (null → HIDDEN 포함 전체 status). */
    public AlbumSearchCondition toAdminCondition() {
        return new AlbumSearchCondition(
                keyword, artistId, genreId, labelId, minPrice, maxPrice,
                minYear, maxYear, format, isLimited, status);
    }
}
