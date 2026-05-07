package com.groove.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 레이블 영속성. {@link GenreRepository} 와 동일 정책 (선검사 + DB UNIQUE).
 */
public interface LabelRepository extends JpaRepository<Label, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Optional<Label> findByName(String name);
}
