package com.groove.stats.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.stats.entity.SalesDailyProduct;
import com.groove.stats.entity.SalesDailyProductId;

public interface SalesDailyProductRepository extends JpaRepository<SalesDailyProduct, SalesDailyProductId> {

	List<SalesDailyProduct> findAllByIdSaleDate(LocalDate saleDate);
}
