package com.groove.catalog.album.application;

import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;

/**
 * 앨범 생성·수정 입력 커맨드. labelId 는 nullable. stock 은 포함하지 않는다.
 */
public record AlbumCommand(
        String title,
        Long artistId,
        Long genreId,
        Long labelId,
        short releaseYear,
        AlbumFormat format,
        long price,
        AlbumStatus status,
        boolean limited,
        String coverImageUrl,
        String description
) {
}
