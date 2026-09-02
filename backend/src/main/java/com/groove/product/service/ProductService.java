package com.groove.product.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.PageResponse;
import com.groove.product.dto.ProductSearchCondition;
import com.groove.product.dto.ProductSearchRequest;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.mapper.ProductSearchMapper;

import lombok.RequiredArgsConstructor;

/** 상품 목록 검색. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {

	private final ProductSearchMapper productSearchMapper;

	public PageResponse<ProductSummaryResponse> search(ProductSearchRequest request) {
		ProductSearchCondition condition = request.toCondition();
		long totalElements = productSearchMapper.countProducts(condition);
		if (totalElements == 0) {
			return PageResponse.of(List.of(), condition.page(), condition.size(), 0);
		}
		List<ProductSummaryResponse> content = productSearchMapper.searchProducts(condition);
		return PageResponse.of(content, condition.page(), condition.size(), totalElements);
	}
}
