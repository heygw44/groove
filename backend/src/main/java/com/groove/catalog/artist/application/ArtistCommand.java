package com.groove.catalog.artist.application;

/**
 * 아티스트 생성·수정 입력 커맨드.
 *
 * <p>HTTP 검증(Bean Validation) 을 통과한 값만 들어온다고 가정한다. {@code description} 은 nullable
 * (DB TEXT NULL) — 갱신 시 {@code null} 을 명시적 지움으로 처리한다.
 */
public record ArtistCommand(String name, String description) {
}
