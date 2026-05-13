package com.groove.catalog.album.api.dto;

import com.groove.catalog.album.application.AlbumRating;
import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;

import java.time.Instant;

/**
 * 앨범 상세 응답 DTO (API §3.3 AlbumDetail).
 *
 * <p>Summary + description / createdAt 추가. artist 는 description 까지 노출 (API §3.3 예시).
 * {@code averageRating}/{@code reviewCount} 는 리뷰 도메인(#59) 의 집계 결과({@link AlbumRating})로 채운다 —
 * 단건 조회라 {@code AlbumService.findDetail} 이 집계 쿼리를 1회 돌려 주입한다. 인자 없는 {@link #from(Album)} 은
 * {@link AlbumRating#NONE} 으로 위임하는 편의 메서드다.
 */
public record AlbumDetailResponse(
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
        Double averageRating,
        long reviewCount,
        Instant createdAt
) {

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

    public record ArtistRef(Long id, String name, String description) {
        static ArtistRef from(Artist artist) {
            return new ArtistRef(artist.getId(), artist.getName(), artist.getDescription());
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
