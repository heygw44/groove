package com.groove.catalog.album.domain;

/**
 * 앨범 포맷 (ERD §4.6 format).
 *
 * <ul>
 *   <li>{@code LP_12} — 12인치 LP (스탠다드)</li>
 *   <li>{@code LP_DOUBLE} — 더블 LP</li>
 *   <li>{@code EP} — Extended Play</li>
 *   <li>{@code SINGLE_7} — 7인치 싱글</li>
 * </ul>
 */
public enum AlbumFormat {
    LP_12,
    LP_DOUBLE,
    EP,
    SINGLE_7
}
