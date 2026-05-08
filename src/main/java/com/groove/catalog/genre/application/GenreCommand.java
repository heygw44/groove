package com.groove.catalog.genre.application;

/**
 * 장르 생성·수정 입력 커맨드.
 *
 * <p>HTTP 검증(Bean Validation) 을 통과한 값만 들어온다고 가정하므로 추가 검증은 수행하지 않는다.
 */
public record GenreCommand(String name) {
}
