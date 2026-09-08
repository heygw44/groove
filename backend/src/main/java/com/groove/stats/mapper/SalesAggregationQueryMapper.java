package com.groove.stats.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.groove.stats.dto.DailySalesAggregateRow;
import com.groove.stats.dto.ProductSalesAggregateRow;

/** 사전 집계 테이블 적재를 위해 하루치 판매를 원본에서 다시 읽는다. 읽기 전용, 쓰기 쿼리는 넣지 않는다. */
@Mapper
public interface SalesAggregationQueryMapper {

	DailySalesAggregateRow findDailySalesOf(@Param("saleDate") LocalDate saleDate);

	List<ProductSalesAggregateRow> findProductSalesOf(@Param("saleDate") LocalDate saleDate);
}
