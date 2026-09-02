package com.groove.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.product.entity.Artist;

public interface ArtistRepository extends JpaRepository<Artist, Long> {
}
