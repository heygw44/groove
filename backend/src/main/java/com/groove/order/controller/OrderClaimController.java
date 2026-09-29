package com.groove.order.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.order.dto.OrderItemResponse;
import com.groove.order.service.OrderItemClaimService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "OrderClaim", description = "취소·반품 클레임")
@RestController
@RequestMapping("/api/v1/order-claims")
@RequiredArgsConstructor
public class OrderClaimController {

	private final OrderItemClaimService orderItemClaimService;

	@Operation(summary = "취소·반품 요청 철회")
	@PostMapping("/{claimId}/withdraw")
	public ApiResponse<OrderItemResponse> withdraw(@AuthMember LoginMember loginMember, @PathVariable Long claimId) {
		return ApiResponse.ok(orderItemClaimService.withdraw(loginMember.id(), claimId));
	}
}
