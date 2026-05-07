package com.groove.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 장르 영속성.
 *
 * <p>{@link #existsByName} 은 생성 시 빠른 409 응답을 위한 선검사이고,
 * {@link #existsByNameAndIdNot} 는 자기 자신 행을 제외한 충돌 검사 (이름 변경 시 사용).
 * 동시 INSERT 에 대한 최종 방어선은 DB UNIQUE (uk_genre_name) 다.
 */
public interface GenreRepository extends JpaRepository<Genre, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Optional<Genre> findByName(String name);
}
