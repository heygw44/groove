package com.groove.product.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.product.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

	List<ProductImage> findAllByProductIdOrderBySortOrderAsc(Long productId);
}
