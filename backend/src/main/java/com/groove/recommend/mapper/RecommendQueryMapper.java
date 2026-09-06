package com.groove.recommend.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.groove.product.dto.ProductSummaryResponse;
import com.groove.recommend.dto.CoPurchaseRow;
import com.groove.recommend.dto.ProductFeatureRow;

/** 추천 도메인 읽기 전용 조회(최근 본 상품 요약, DB 폴백, 공동구매 집계, 상품 특성)만 담당한다. */
@Mapper
public interface RecommendQueryMapper {

	List<ProductSummaryResponse> findSummariesByIds(@Param("ids") List<Long> ids, @Param("memberId") Long memberId);

	List<Long> findRecentProductIds(@Param("memberId") Long memberId, @Param("limit") int limit);

	List<CoPurchaseRow> countCoPurchases(@Param("sinceAt") LocalDateTime sinceAt);

	List<ProductFeatureRow> findProductFeatures();
}
