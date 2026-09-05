package com.groove.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.product.entity.Genre;

public interface GenreRepository extends JpaRepository<Genre, Long> {

	Optional<Genre> findByName(String name);

	boolean existsByName(String name);

	List<Genre> findAllByOrderByNameAsc();
}
