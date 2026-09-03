package com.groove.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;
import com.groove.order.dto.AdminOrderSearchRequest;
import com.groove.order.dto.AdminOrderStatusChangeRequest;
import com.groove.order.dto.AdminOrderSummaryResponse;
import com.groove.order.dto.OrderDetailResponse;
import com.groove.order.service.AdminOrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Order", description = "관리자 주문 관리")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

	private final AdminOrderService adminOrderService;

	@Operation(summary = "관리자 주문 목록 조회")
	@GetMapping
	public ApiResponse<PageResponse<AdminOrderSummaryResponse>> getList(
			@Valid @ModelAttribute AdminOrderSearchRequest request) {
		return ApiResponse.ok(adminOrderService.getList(request));
	}

	@Operation(summary = "주문 상태 전이")
	@PatchMapping("/{id}/status")
	public ApiResponse<OrderDetailResponse> changeStatus(@AuthMember LoginMember admin, @PathVariable Long id,
			@Valid @RequestBody AdminOrderStatusChangeRequest request) {
		return ApiResponse.ok(adminOrderService.changeStatus(admin.id(), id, request));
	}
}
