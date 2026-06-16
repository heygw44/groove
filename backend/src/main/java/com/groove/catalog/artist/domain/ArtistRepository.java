package com.groove.catalog.artist.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** 아티스트 영속성. 페이징은 JpaRepository.findAll(Pageable) 을 그대로 활용한다. */
public interface ArtistRepository extends JpaRepository<Artist, Long> {
}
