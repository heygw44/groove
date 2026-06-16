package com.groove.catalog.album.domain;

/**
 * 앨범 판매 상태. SELLING 만 Public 카탈로그에 노출, SOLD_OUT 은 재고 0, HIDDEN 은 비공개.
 */
public enum AlbumStatus {
    SELLING,
    SOLD_OUT,
    HIDDEN
}
