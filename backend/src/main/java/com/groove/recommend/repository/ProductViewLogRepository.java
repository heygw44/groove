package com.groove.recommend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.recommend.entity.ProductViewLog;

public interface ProductViewLogRepository extends JpaRepository<ProductViewLog, Long> {
}
