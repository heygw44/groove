package com.groove.catalog.artist.application;

/** 아티스트 생성·수정 입력 커맨드. description 은 nullable, 갱신 시 null 은 명시적 지움. */
public record ArtistCommand(String name, String description) {
}
