package com.groove.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.SliceResponse;
import com.groove.order.dto.AdminOrderItemBulkResultResponse;
import com.groove.order.dto.AdminOrderItemConfirmRequest;
import com.groove.order.dto.AdminOrderItemCountResponse;
import com.groove.order.dto.AdminOrderItemDeliverRequest;
import com.groove.order.dto.AdminOrderItemSearchRequest;
import com.groove.order.dto.AdminOrderItemShipRequest;
import com.groove.order.dto.AdminOrderItemSummaryResponse;
import com.groove.order.service.AdminOrderItemService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Order Item", description = "관리자 상품주문 관리")
@RestController
@RequestMapping("/api/v1/admin/order-items")
@RequiredArgsConstructor
public class AdminOrderItemController {

	private final AdminOrderItemService adminOrderItemService;

	@Operation(summary = "상품주문 목록 조회")
	@GetMapping
	public ApiResponse<SliceResponse<AdminOrderItemSummaryResponse>> getList(
			@Valid @ModelAttribute AdminOrderItemSearchRequest request) {
		return ApiResponse.ok(adminOrderItemService.getList(request));
	}

	@Operation(summary = "상품주문 건수 조회")
	@GetMapping("/count")
	public ApiResponse<AdminOrderItemCountResponse> count(
			@Valid @ModelAttribute AdminOrderItemSearchRequest request) {
		return ApiResponse.ok(adminOrderItemService.count(request));
	}

	@Operation(summary = "상품주문 일괄 발주확인")
	@PostMapping("/confirm")
	public ApiResponse<AdminOrderItemBulkResultResponse> confirmPreparing(@AuthMember LoginMember admin,
			@Valid @RequestBody AdminOrderItemConfirmRequest request) {
		return ApiResponse.ok(adminOrderItemService.confirmPreparing(admin.id(), request));
	}

	@Operation(summary = "상품주문 일괄 발송처리")
	@PostMapping("/ship")
	public ApiResponse<AdminOrderItemBulkResultResponse> startShipping(@AuthMember LoginMember admin,
			@Valid @RequestBody AdminOrderItemShipRequest request) {
		return ApiResponse.ok(adminOrderItemService.startShipping(admin.id(), request));
	}

	@Operation(summary = "상품주문 일괄 배송완료 처리")
	@PostMapping("/deliver")
	public ApiResponse<AdminOrderItemBulkResultResponse> completeDelivery(@AuthMember LoginMember admin,
			@Valid @RequestBody AdminOrderItemDeliverRequest request) {
		return ApiResponse.ok(adminOrderItemService.completeDelivery(admin.id(), request));
	}
}
