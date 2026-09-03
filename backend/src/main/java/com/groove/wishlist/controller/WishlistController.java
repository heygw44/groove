package com.groove.wishlist.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.groove.wishlist.dto.WishlistAddRequest;
import com.groove.wishlist.dto.WishlistItemResponse;
import com.groove.wishlist.dto.WishlistSearchRequest;
import com.groove.wishlist.service.WishlistService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Wishlist", description = "위시리스트")
@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

	private final WishlistService wishlistService;

	@Operation(summary = "내 위시리스트 조회")
	@GetMapping
	public ApiResponse<PageResponse<WishlistItemResponse>> getWishlist(@AuthMember LoginMember loginMember,
			@Valid @ModelAttribute WishlistSearchRequest request) {
		return ApiResponse.ok(wishlistService.getWishlist(loginMember.id(), request));
	}

	@Operation(summary = "위시리스트 등록")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<WishlistItemResponse> add(@AuthMember LoginMember loginMember,
			@Valid @RequestBody WishlistAddRequest request) {
		return ApiResponse.ok(wishlistService.add(loginMember.id(), request));
	}

	@Operation(summary = "위시리스트 삭제")
	@DeleteMapping("/{productId}")
	public ApiResponse<Void> remove(@AuthMember LoginMember loginMember, @PathVariable Long productId) {
		wishlistService.remove(loginMember.id(), productId);
		return ApiResponse.ok();
	}
}
