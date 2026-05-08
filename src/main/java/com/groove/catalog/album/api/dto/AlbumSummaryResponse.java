package com.groove.catalog.album.api.dto;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;

/**
 * 앨범 목록 응답 DTO (API §3.3 AlbumSummary).
 *
 * <p>{@code averageRating} / {@code reviewCount} 는 W7 (review 도메인) 도입 전이라 placeholder
 * 로 채운다 — null / 0. 도메인 도입 시 집계 쿼리·캐시로 채우도록 시그니처는 미리 노출한다.
 *
 * <p>관리자용 {@link AlbumResponse} 와 분리한 이유: Public 응답은 createdAt/updatedAt 을
 * 노출하지 않고 평점·리뷰 수를 포함하므로 응집도 위해 별도 record 로 둔다.
 */
public record AlbumSummaryResponse(
        Long id,
        String title,
        ArtistRef artist,
        GenreRef genre,
        LabelRef label,
        short releaseYear,
        AlbumFormat format,
        long price,
        int stock,
        AlbumStatus status,
        boolean isLimited,
        String coverImageUrl,
        Double averageRating,
        long reviewCount
) {

    public static AlbumSummaryResponse from(Album album) {
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
                null,
                0L
        );
    }

    public record ArtistRef(Long id, String name) {
        static ArtistRef from(Artist artist) {
            return new ArtistRef(artist.getId(), artist.getName());
        }
    }

    public record GenreRef(Long id, String name) {
        static GenreRef from(Genre genre) {
            return new GenreRef(genre.getId(), genre.getName());
        }
    }

    public record LabelRef(Long id, String name) {
        static LabelRef from(Label label) {
            return label == null ? null : new LabelRef(label.getId(), label.getName());
        }
    }
}
