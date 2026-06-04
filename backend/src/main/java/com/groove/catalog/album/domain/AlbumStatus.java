package com.groove.catalog.album.domain;

/**
 * 앨범 판매 상태 (ERD §4.6 status).
 *
 * <p>{@code SELLING} 만 Public 카탈로그에 노출되며, {@code SOLD_OUT} 은 재고 0 시 자동 또는
 * 관리자 수동 전이, {@code HIDDEN} 은 비공개 (관리자 수동). 상태 머신 전이 룰은
 * 후속 이슈 (재고/주문 흐름) 에서 {@code canTransitionTo} 로 도입한다 — W5-3 범위에서는 단순 set 만 허용.
 */
public enum AlbumStatus {
    SELLING,
    SOLD_OUT,
    HIDDEN
}
