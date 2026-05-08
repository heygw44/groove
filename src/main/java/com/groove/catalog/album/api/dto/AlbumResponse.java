package com.groove.catalog.album.api.dto;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;

import java.time.Instant;

/**
 * 앨범 응답 DTO (API §3.3 / §3.9).
 *
 * <p>API §3.3 의 AlbumDetail 형태에서 averageRating/reviewCount 는 W7 (리뷰 도메인) 도입 후 추가한다.
 * artist/genre/label 은 중첩 요약 객체. label 은 nullable.
 */
public record AlbumResponse(
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
        String description,
        Instant createdAt,
        Instant updatedAt
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
