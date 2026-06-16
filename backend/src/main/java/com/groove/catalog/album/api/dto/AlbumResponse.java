package com.groove.catalog.album.api.dto;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** 앨범 응답 DTO. artist/genre/label 은 중첩 요약 객체, label 은 nullable. */
public record AlbumResponse(
        @Schema(description = "앨범 ID", example = "1") Long id,
        @Schema(description = "앨범 제목", example = "Kind of Blue") String title,
        ArtistRef artist,
        GenreRef genre,
        @Schema(description = "레이블 요약 (nullable)") LabelRef label,
        @Schema(description = "발매 연도", example = "1959") short releaseYear,
        @Schema(description = "앨범 포맷", example = "LP") AlbumFormat format,
        @Schema(description = "판매 가격 (원)", example = "38000") long price,
        @Schema(description = "현재 재고 수량", example = "100") int stock,
        @Schema(description = "판매 상태", example = "SELLING") AlbumStatus status,
        @Schema(description = "한정반 여부", example = "false") boolean isLimited,
        @Schema(description = "커버 이미지 URL (nullable)", example = "https://cdn.groove.example/covers/kob.jpg") String coverImageUrl,
        @Schema(description = "앨범 설명 (nullable)", example = "Miles Davis 의 모달 재즈 명반") String description,
        @Schema(description = "생성 시각", example = "2026-01-15T09:30:00Z") Instant createdAt,
        @Schema(description = "수정 시각", example = "2026-01-20T11:00:00Z") Instant updatedAt
) {
    public static AlbumResponse from(Album album) {
        return new AlbumResponse(
                album.getId(),
                album.getTitle(),
                ArtistRef.from(album.getArtist()),
                GenreRef.from(album.getGenre()),
                LabelRef.from(album.getLabel()),
                album.getReleaseYear(),
                album.getFormat(),
                album.getPrice(),
                album.getStock(),
                album.getStatus(),
                album.isLimited(),
                album.getCoverImageUrl(),
                album.getDescription(),
                album.getCreatedAt(),
                album.getUpdatedAt()
        );
    }

    public record ArtistRef(
            @Schema(description = "아티스트 ID", example = "1") Long id,
            @Schema(description = "아티스트 이름", example = "Miles Davis") String name) {
        static ArtistRef from(Artist artist) {
            return new ArtistRef(artist.getId(), artist.getName());
        }
    }

    public record GenreRef(
            @Schema(description = "장르 ID", example = "3") Long id,
            @Schema(description = "장르 이름", example = "Jazz") String name) {
        static GenreRef from(Genre genre) {
            return new GenreRef(genre.getId(), genre.getName());
        }
    }

    public record LabelRef(
            @Schema(description = "레이블 ID", example = "5") Long id,
            @Schema(description = "레이블 이름", example = "Columbia") String name) {
        static LabelRef from(Label label) {
            return label == null ? null : new LabelRef(label.getId(), label.getName());
        }
    }
}
