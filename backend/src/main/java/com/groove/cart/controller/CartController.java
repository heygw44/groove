package com.groove.cart.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.cart.dto.CartItemAddRequest;
import com.groove.cart.dto.CartItemQuantityUpdateRequest;
import com.groove.cart.dto.CartItemResponse;
import com.groove.cart.dto.CartResponse;
import com.groove.cart.service.CartService;
import com.groove.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Cart", description = "장바구니")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

	private final CartService cartService;

	@Operation(summary = "장바구니 조회")
	@GetMapping
	public ApiResponse<CartResponse> getCart(@AuthMember LoginMember loginMember) {
		return ApiResponse.ok(cartService.getCart(loginMember.id()));
	}

	@Operation(summary = "장바구니에 상품 담기")
	@PostMapping("/items")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CartItemResponse> addItem(@AuthMember LoginMember loginMember,
			@Valid @RequestBody CartItemAddRequest request) {
		return ApiResponse.ok(cartService.addItem(loginMember.id(), request));
	}

	@Operation(summary = "장바구니 상품 수량 변경")
	@PatchMapping("/items/{cartItemId}")
	public ApiResponse<CartItemResponse> updateQuantity(@AuthMember LoginMember loginMember,
			@PathVariable Long cartItemId, @Valid @RequestBody CartItemQuantityUpdateRequest request) {
		return ApiResponse.ok(cartService.updateQuantity(loginMember.id(), cartItemId, request));
	}

	@Operation(summary = "장바구니 상품 삭제")
	@DeleteMapping("/items/{cartItemId}")
	public ApiResponse<Void> removeItem(@AuthMember LoginMember loginMember, @PathVariable Long cartItemId) {
		cartService.removeItem(loginMember.id(), cartItemId);
		return ApiResponse.ok();
	}

	@Operation(summary = "장바구니 비우기")
	@DeleteMapping("/items")
	public ApiResponse<Void> clear(@AuthMember LoginMember loginMember) {
		cartService.clear(loginMember.id());
		return ApiResponse.ok();
	}
}
