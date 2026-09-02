package com.groove.product.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.product.entity.Artist;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

	@Query("""
			SELECT a FROM Artist a
			WHERE :keyword IS NULL
				OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
				OR LOWER(a.nameEn) LIKE LOWER(CONCAT('%', :keyword, '%'))
			ORDER BY a.name ASC
			""")
	List<Artist> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
