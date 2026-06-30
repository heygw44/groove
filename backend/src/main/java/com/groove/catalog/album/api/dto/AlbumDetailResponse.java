package com.groove.catalog.album.api.dto;

import com.groove.catalog.album.application.AlbumRating;
import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

// Serializable: 분산 Redis 캐시 JDK 직렬화 대상. serialVersionUID 고정 — 필드 변경 시 자동 UID 가 흔들려 역직렬화가 깨지지 않게.
/** averageRating/reviewCount 는 AlbumRating 으로 채운다. 인자 없는 from(Album) 은 AlbumRating.NONE. */
public record AlbumDetailResponse(
        @Schema(description = "앨범 ID", example = "1") Long id,
        @Schema(description = "앨범 제목", example = "Random Access Memories") String title,
        @Schema(description = "아티스트 정보") ArtistRef artist,
        @Schema(description = "장르 정보") GenreRef genre,
        @Schema(description = "레이블 정보 (없을 수 있음)") LabelRef label,
        @Schema(description = "발매 연도", example = "2013") short releaseYear,
        @Schema(description = "음반 포맷", example = "VINYL") AlbumFormat format,
        @Schema(description = "판매 가격 (원)", example = "39000") long price,
        @Schema(description = "재고 수량", example = "25") int stock,
        @Schema(description = "판매 상태", example = "SELLING") AlbumStatus status,
        @Schema(description = "한정판 여부", example = "false") boolean isLimited,
        @Schema(description = "커버 이미지 URL", example = "https://cdn.groove.com/covers/1.jpg") String coverImageUrl,
        @Schema(description = "앨범 설명", example = "다프트 펑크의 네 번째 정규 앨범") String description,
        @Schema(description = "평균 평점 (리뷰 없으면 null)", example = "4.5") Double averageRating,
        @Schema(description = "리뷰 수", example = "128") long reviewCount,
        @Schema(description = "등록 일시 (ISO-8601)", example = "2026-01-15T09:30:00Z") Instant createdAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static AlbumDetailResponse from(Album album) {
        return from(album, AlbumRating.NONE);
    }

    public static AlbumDetailResponse from(Album album, AlbumRating rating) {
        return new AlbumDetailResponse(
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
                rating.averageRating(),
                rating.reviewCount(),
                album.getCreatedAt()
        );
    }

    public record ArtistRef(
            @Schema(description = "아티스트 ID", example = "1") Long id,
            @Schema(description = "아티스트 이름", example = "Daft Punk") String name,
            @Schema(description = "아티스트 설명", example = "프랑스의 일렉트로닉 듀오") String description) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        static ArtistRef from(Artist artist) {
            return new ArtistRef(artist.getId(), artist.getName(), artist.getDescription());
        }
    }

    public record GenreRef(
            @Schema(description = "장르 ID", example = "3") Long id,
            @Schema(description = "장르 이름", example = "Electronic") String name) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        static GenreRef from(Genre genre) {
            return new GenreRef(genre.getId(), genre.getName());
        }
    }

    public record LabelRef(
            @Schema(description = "레이블 ID", example = "7") Long id,
            @Schema(description = "레이블 이름", example = "Columbia Records") String name) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        static LabelRef from(Label label) {
            return label == null ? null : new LabelRef(label.getId(), label.getName());
        }
    }
}
