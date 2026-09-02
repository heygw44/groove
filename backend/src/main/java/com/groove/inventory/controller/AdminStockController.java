package com.groove.inventory.controller;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.global.common.ApiResponse;
import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.dto.StockResponse;
import com.groove.inventory.service.StockService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Stock", description = "관리자 재고 조정")
@RestController
@RequestMapping("/api/v1/admin/products/{productId}/stock")
@RequiredArgsConstructor
public class AdminStockController {

	private final StockService stockService;

	@Operation(summary = "재고 조정")
	@PatchMapping
	public ApiResponse<StockResponse> adjust(@PathVariable Long productId,
			@Valid @RequestBody StockAdjustRequest request) {
		return ApiResponse.ok(stockService.adjust(productId, request));
	}
}
