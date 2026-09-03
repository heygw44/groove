package com.groove.order.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderCreateResponse;
import com.groove.order.dto.OrderDetailResponse;
import com.groove.order.dto.OrderSearchRequest;
import com.groove.order.dto.OrderSummaryResponse;
import com.groove.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Order", description = "주문")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	@Operation(summary = "주문 생성")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<OrderCreateResponse> create(@AuthMember LoginMember loginMember,
			@Valid @RequestBody OrderCreateRequest request) {
		return ApiResponse.ok(orderService.create(loginMember.id(), request));
	}

	@Operation(summary = "내 주문 목록 조회")
	@GetMapping
	public ApiResponse<PageResponse<OrderSummaryResponse>> getMyOrders(@AuthMember LoginMember loginMember,
			@Valid @ModelAttribute OrderSearchRequest request) {
		return ApiResponse.ok(orderService.getMyOrders(loginMember.id(), request));
	}

	@Operation(summary = "주문 상세 조회")
	@GetMapping("/{id}")
	public ApiResponse<OrderDetailResponse> getDetail(@AuthMember LoginMember loginMember, @PathVariable Long id) {
		return ApiResponse.ok(orderService.getDetail(loginMember.id(), id));
	}

	@Operation(summary = "주문 취소")
	@PostMapping("/{id}/cancel")
	public ApiResponse<OrderDetailResponse> cancel(@AuthMember LoginMember loginMember, @PathVariable Long id,
			@RequestBody(required = false) @Valid OrderCancelRequest request) {
		return ApiResponse.ok(orderService.cancel(loginMember.id(), id, request));
	}
}
