package com.groove.product.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.product.entity.Album;

public interface AlbumRepository extends JpaRepository<Album, Long> {

	@EntityGraph(attributePaths = "artist")
	Optional<Album> findWithArtistById(Long id);

	Optional<Album> findByDiscogsMasterId(Long discogsMasterId);

	Optional<Album> findFirstByTitleAndArtistIdOrderByIdAsc(String title, Long artistId);

	@Query(value = """
			SELECT a FROM Album a JOIN FETCH a.artist ar
			WHERE :keyword IS NULL
				OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
				OR LOWER(ar.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
			""",
			countQuery = """
			SELECT COUNT(a) FROM Album a JOIN a.artist ar
			WHERE :keyword IS NULL
				OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
				OR LOWER(ar.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
			""")
	Page<Album> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
