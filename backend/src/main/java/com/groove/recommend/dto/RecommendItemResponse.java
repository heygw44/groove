package com.groove.recommend.dto;

import java.util.List;

import com.groove.product.dto.ProductSummaryResponse;

public record RecommendItemResponse(ProductSummaryResponse product, List<RecommendReason> reasons) {
}
