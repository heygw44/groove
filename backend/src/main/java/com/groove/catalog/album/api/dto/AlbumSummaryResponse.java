package com.groove.catalog.album.api.dto;

import com.groove.catalog.album.application.AlbumRating;
import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 앨범 목록 응답 DTO. averageRating/reviewCount 는 AlbumRating 으로 채우고, 리뷰가 없으면
 * averageRating=null, reviewCount=0. 인자 없는 from(Album) 은 AlbumRating.NONE 으로 위임한다.
 */
public record AlbumSummaryResponse(
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
        @Schema(description = "평균 평점 (리뷰 없으면 null)", example = "4.5") Double averageRating,
        @Schema(description = "리뷰 수", example = "128") long reviewCount
) {

    public static AlbumSummaryResponse from(Album album) {
        return from(album, AlbumRating.NONE);
    }

    public static AlbumSummaryResponse from(Album album, AlbumRating rating) {
        return new AlbumSummaryResponse(
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
                rating.averageRating(),
                rating.reviewCount()
        );
    }

    public record ArtistRef(
            @Schema(description = "아티스트 ID", example = "1") Long id,
            @Schema(description = "아티스트 이름", example = "Daft Punk") String name) {
        static ArtistRef from(Artist artist) {
            return new ArtistRef(artist.getId(), artist.getName());
        }
    }

    public record GenreRef(
            @Schema(description = "장르 ID", example = "3") Long id,
            @Schema(description = "장르 이름", example = "Electronic") String name) {
        static GenreRef from(Genre genre) {
            return new GenreRef(genre.getId(), genre.getName());
        }
    }

    public record LabelRef(
            @Schema(description = "레이블 ID", example = "7") Long id,
            @Schema(description = "레이블 이름", example = "Columbia Records") String name) {
        static LabelRef from(Label label) {
            return label == null ? null : new LabelRef(label.getId(), label.getName());
        }
    }
}
