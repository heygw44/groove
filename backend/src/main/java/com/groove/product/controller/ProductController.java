package com.groove.product.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;
import com.groove.product.dto.ProductSearchRequest;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Product", description = "상품 조회")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

	private final ProductService productService;

	@Operation(summary = "상품 목록 검색")
	@SecurityRequirements
	@GetMapping
	public ApiResponse<PageResponse<ProductSummaryResponse>> search(
			@Valid @ModelAttribute ProductSearchRequest request) {
		return ApiResponse.ok(productService.search(request));
	}
}
