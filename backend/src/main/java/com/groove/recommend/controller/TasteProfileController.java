package com.groove.recommend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.recommend.dto.TasteProfileResponse;
import com.groove.recommend.dto.TasteProfileUpdateRequest;
import com.groove.recommend.service.TasteProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "TasteProfile", description = "취향 프로필")
@RestController
@RequestMapping("/api/v1/members/me/taste-profile")
@RequiredArgsConstructor
public class TasteProfileController {

	private final TasteProfileService tasteProfileService;

	@Operation(summary = "내 취향 프로필 조회")
	@GetMapping
	public ApiResponse<TasteProfileResponse> getMyProfile(@AuthMember LoginMember loginMember) {
		return ApiResponse.ok(tasteProfileService.getMyProfile(loginMember.id()));
	}

	@Operation(summary = "취향 프로필 생성/수정")
	@PutMapping
	public ApiResponse<TasteProfileResponse> update(@AuthMember LoginMember loginMember,
			@Valid @RequestBody TasteProfileUpdateRequest request) {
		return ApiResponse.ok(tasteProfileService.update(loginMember.id(), request));
	}
}
