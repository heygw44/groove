package com.groove.product.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.product.entity.ProductGenre;

public interface ProductGenreRepository extends JpaRepository<ProductGenre, Long> {

	List<ProductGenre> findAllByProductId(Long productId);
}
