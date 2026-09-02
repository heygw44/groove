package com.groove.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.inventory.entity.StockHistory;

public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

	List<StockHistory> findAllByStockIdOrderByCreatedAtAsc(Long stockId);
}
