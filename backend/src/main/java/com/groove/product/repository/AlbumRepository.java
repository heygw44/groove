package com.groove.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.product.entity.Album;

public interface AlbumRepository extends JpaRepository<Album, Long> {

	@EntityGraph(attributePaths = "artist")
	Optional<Album> findWithArtistById(Long id);
}
