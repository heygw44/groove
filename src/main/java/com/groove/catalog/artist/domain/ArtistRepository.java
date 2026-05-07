package com.groove.catalog.artist.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 아티스트 영속성.
 *
 * <p>name 에 UNIQUE 를 두지 않으므로 Genre/Label 처럼 {@code existsByName} 류 메서드는 두지 않는다.
 * 페이징은 {@link JpaRepository#findAll(org.springframework.data.domain.Pageable)} 을 그대로 활용한다.
 */
public interface ArtistRepository extends JpaRepository<Artist, Long> {
}
