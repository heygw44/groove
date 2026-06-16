package com.groove.catalog.genre.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 장르 영속성. existsByName 은 생성 시 중복 선검사, existsByNameAndIdNot 은
 * 자기 행을 제외한 이름 충돌 검사. 최종 방어선은 DB UNIQUE.
 */
public interface GenreRepository extends JpaRepository<Genre, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Optional<Genre> findByName(String name);
}
