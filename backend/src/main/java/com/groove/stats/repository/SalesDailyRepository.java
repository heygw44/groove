package com.groove.stats.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.stats.entity.SalesDaily;

public interface SalesDailyRepository extends JpaRepository<SalesDaily, LocalDate> {

	List<SalesDaily> findAllBySaleDateBetweenOrderBySaleDate(LocalDate from, LocalDate to);
}
