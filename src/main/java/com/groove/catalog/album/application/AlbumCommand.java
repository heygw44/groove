package com.groove.catalog.album.application;

import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;

/**
 * 앨범 생성·수정 입력 커맨드.
 *
 * <p>HTTP 검증(Bean Validation) 을 통과한 값만 들어온다고 가정한다. {@code labelId} 는 nullable
 * (레이블 정보 없는 앨범 허용 — ERD §4.6).
 *
 * <p>{@code stock} 은 본 커맨드에 포함되지 않는다 — 변경은 {@code adjustStock} 으로만 가능하며
 * 생성 시 초기 재고는 {@link AlbumService#create} 에 별도 인자로 전달한다 (책임 분리).
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
