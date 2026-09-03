package com.groove.coupon.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.coupon.dto.AvailableCouponResponse;
import com.groove.coupon.dto.CouponIssueRequest;
import com.groove.coupon.dto.CouponIssueResponse;
import com.groove.coupon.service.MemberCouponService;
import com.groove.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Coupon", description = "쿠폰 발급/조회")
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

	private final MemberCouponService memberCouponService;

	@Operation(summary = "쿠폰 코드로 발급")
	@PostMapping("/issue")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CouponIssueResponse> issue(@AuthMember LoginMember loginMember,
			@Valid @RequestBody CouponIssueRequest request) {
		return ApiResponse.ok(memberCouponService.issue(loginMember.id(), request));
	}

	@Operation(summary = "주문 시 적용 가능한 쿠폰 조회")
	@GetMapping("/available")
	public ApiResponse<List<AvailableCouponResponse>> getAvailable(@AuthMember LoginMember loginMember,
			@RequestParam BigDecimal orderAmount) {
		return ApiResponse.ok(memberCouponService.getAvailableCoupons(loginMember.id(), orderAmount));
	}
}
