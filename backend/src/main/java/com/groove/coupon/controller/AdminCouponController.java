package com.groove.coupon.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.coupon.dto.AdminCouponResponse;
import com.groove.coupon.dto.AdminCouponSummaryResponse;
import com.groove.coupon.dto.CouponCreateRequest;
import com.groove.coupon.dto.CouponUpdateRequest;
import com.groove.coupon.entity.CouponStatus;
import com.groove.coupon.service.AdminCouponService;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Coupon", description = "관리자 쿠폰 관리")
@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

	private final AdminCouponService adminCouponService;

	@Operation(summary = "쿠폰 등록")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AdminCouponResponse> create(@AuthMember LoginMember loginMember,
			@Valid @RequestBody CouponCreateRequest request) {
		return ApiResponse.ok(adminCouponService.create(loginMember.id(), request));
	}

	@Operation(summary = "쿠폰 목록 조회")
	@GetMapping
	public ApiResponse<PageResponse<AdminCouponSummaryResponse>> getList(
			@RequestParam(required = false) CouponStatus status,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(adminCouponService.getList(status, pageable));
	}

	@Operation(summary = "쿠폰 수정")
	@PatchMapping("/{id}")
	public ApiResponse<AdminCouponResponse> update(@AuthMember LoginMember loginMember, @PathVariable Long id,
			@Valid @RequestBody CouponUpdateRequest request) {
		return ApiResponse.ok(adminCouponService.update(loginMember.id(), id, request));
	}

	@Operation(summary = "쿠폰 비활성화")
	@DeleteMapping("/{id}")
	public ApiResponse<Void> disable(@AuthMember LoginMember loginMember, @PathVariable Long id) {
		adminCouponService.disable(loginMember.id(), id);
		return ApiResponse.ok();
	}
}
