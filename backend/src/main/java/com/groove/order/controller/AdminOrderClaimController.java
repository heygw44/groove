package com.groove.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;
import com.groove.order.dto.AdminOrderClaimCompleteRequest;
import com.groove.order.dto.AdminOrderClaimCountResponse;
import com.groove.order.dto.AdminOrderClaimRejectRequest;
import com.groove.order.dto.AdminOrderClaimSearchRequest;
import com.groove.order.dto.AdminOrderClaimSummaryResponse;
import com.groove.order.dto.AdminOrderItemCancelRequest;
import com.groove.order.dto.AdminOrderItemResponse;
import com.groove.order.service.AdminOrderClaimService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin OrderClaim", description = "관리자 취소·반품 클레임")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminOrderClaimController {

	private final AdminOrderClaimService adminOrderClaimService;

	@Operation(summary = "취소·반품 클레임 큐 조회")
	@GetMapping("/order-claims")
	public ApiResponse<PageResponse<AdminOrderClaimSummaryResponse>> getList(
			@Valid @ModelAttribute AdminOrderClaimSearchRequest request) {
		return ApiResponse.ok(adminOrderClaimService.getList(request));
	}

	@Operation(summary = "취소·반품 클레임 상태별 건수")
	@GetMapping("/order-claims/counts")
	public ApiResponse<AdminOrderClaimCountResponse> getCounts() {
		return ApiResponse.ok(adminOrderClaimService.getCounts());
	}

	@Operation(summary = "취소 클레임 승인")
	@PostMapping("/order-claims/{id}/approve")
	public ApiResponse<AdminOrderItemResponse> approve(@AuthMember LoginMember admin, @PathVariable Long id) {
		return ApiResponse.ok(adminOrderClaimService.approve(admin.id(), id));
	}

	@Operation(summary = "클레임 거부")
	@PostMapping("/order-claims/{id}/reject")
	public ApiResponse<AdminOrderItemResponse> reject(@AuthMember LoginMember admin, @PathVariable Long id,
			@Valid @RequestBody AdminOrderClaimRejectRequest request) {
		return ApiResponse.ok(adminOrderClaimService.reject(admin.id(), id, request));
	}

	@Operation(summary = "반품 수거 시작")
	@PostMapping("/order-claims/{id}/collect")
	public ApiResponse<AdminOrderItemResponse> collect(@AuthMember LoginMember admin, @PathVariable Long id) {
		return ApiResponse.ok(adminOrderClaimService.collect(admin.id(), id));
	}

	@Operation(summary = "반품 수거 완료")
	@PostMapping("/order-claims/{id}/complete")
	public ApiResponse<AdminOrderItemResponse> complete(@AuthMember LoginMember admin, @PathVariable Long id,
			@Valid @RequestBody AdminOrderClaimCompleteRequest request) {
		return ApiResponse.ok(adminOrderClaimService.complete(admin.id(), id, request));
	}

	@Operation(summary = "상품주문 판매취소")
	@PostMapping("/order-items/{id}/cancel")
	public ApiResponse<AdminOrderItemResponse> cancelItem(@AuthMember LoginMember admin, @PathVariable Long id,
			@RequestBody(required = false) @Valid AdminOrderItemCancelRequest request) {
		return ApiResponse.ok(adminOrderClaimService.cancelItemBySale(admin.id(), id, request));
	}
}
