package com.groove.inventory.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.inventory.entity.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {

	Optional<Stock> findByProductId(Long productId);

	@EntityGraph(attributePaths = "product")
	Optional<Stock> findWithProductByProductId(Long productId);

	List<Stock> findAllByProductIdIn(Collection<Long> productIds);
}
