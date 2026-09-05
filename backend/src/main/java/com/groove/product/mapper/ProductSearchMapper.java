package com.groove.product.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.groove.product.dto.ProductSearchCondition;
import com.groove.product.dto.ProductSummaryResponse;

/** 상품 목록 검색 전용 동적 SQL. 단순 조회는 JPA(ProductRepository)를 쓰고 이 매퍼는 다중 필터+정렬 조합만 담당한다. */
@Mapper
public interface ProductSearchMapper {

	List<ProductSummaryResponse> searchProducts(ProductSearchCondition condition);

	long countProducts(ProductSearchCondition condition);
}
