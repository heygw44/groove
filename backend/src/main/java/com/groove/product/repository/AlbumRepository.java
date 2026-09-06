package com.groove.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.product.entity.Album;

public interface AlbumRepository extends JpaRepository<Album, Long> {
}
