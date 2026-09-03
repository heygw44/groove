package com.groove.coupon.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.coupon.dto.MemberCouponResponse;
import com.groove.coupon.dto.MemberCouponStatus;
import com.groove.coupon.service.MemberCouponService;
import com.groove.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Member Coupon", description = "내 쿠폰함")
@RestController
@RequestMapping("/api/v1/members/me/coupons")
@RequiredArgsConstructor
public class MemberCouponController {

	private final MemberCouponService memberCouponService;

	@Operation(summary = "내 쿠폰 목록 조회")
	@GetMapping
	public ApiResponse<List<MemberCouponResponse>> getMyCoupons(@AuthMember LoginMember loginMember,
			@RequestParam(required = false) String status) {
		return ApiResponse.ok(memberCouponService.getMyCoupons(loginMember.id(), MemberCouponStatus.from(status)));
	}
}
