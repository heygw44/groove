package com.groove.order.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderItemResponse;
import com.groove.order.dto.OrderReturnRequest;
import com.groove.order.service.OrderItemClaimService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Order", description = "주문")
@RestController
@RequestMapping("/api/v1/orders/{orderId}/items/{itemId}")
@RequiredArgsConstructor
public class OrderItemClaimController {

	private final OrderItemClaimService orderItemClaimService;

	@Operation(summary = "상품주문 취소")
	@PostMapping("/cancel")
	public ApiResponse<OrderItemResponse> cancel(@AuthMember LoginMember loginMember, @PathVariable Long orderId,
			@PathVariable Long itemId, @RequestBody(required = false) @Valid OrderCancelRequest request) {
		return ApiResponse.ok(orderItemClaimService.cancel(loginMember.id(), orderId, itemId, request));
	}

	@Operation(summary = "상품주문 반품 요청")
	@PostMapping("/return")
	public ApiResponse<OrderItemResponse> returnItem(@AuthMember LoginMember loginMember, @PathVariable Long orderId,
			@PathVariable Long itemId, @RequestBody(required = false) @Valid OrderReturnRequest request) {
		return ApiResponse.ok(orderItemClaimService.returnItem(loginMember.id(), orderId, itemId, request));
	}
}
