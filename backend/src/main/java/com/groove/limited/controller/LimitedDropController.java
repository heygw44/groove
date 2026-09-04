package com.groove.limited.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.limited.dto.LimitedDropDetailResponse;
import com.groove.limited.dto.LimitedDropListResponse;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.service.LimitedDropService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 한정반 공개 조회. serverTime 이 카운트다운 기준 시각으로 실리므로 캐시하지 않는다. */
@Tag(name = "Limited Drop", description = "한정반 조회")
@RestController
@RequestMapping("/api/v1/limited-drops")
@RequiredArgsConstructor
public class LimitedDropController {

	private final LimitedDropService limitedDropService;

	@Operation(summary = "한정반 목록 조회", description = "status 미지정 시 SCHEDULED·OPEN·SOLD_OUT 을 반환")
	@SecurityRequirements
	@GetMapping
	public ResponseEntity<ApiResponse<LimitedDropListResponse>> getList(
			@RequestParam(required = false) LimitedDropStatus status) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.ok(limitedDropService.getList(status)));
	}

	@Operation(summary = "한정반 상세 조회", description = "로그인 시 purchased 포함")
	@SecurityRequirements
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<LimitedDropDetailResponse>> getDetail(@PathVariable Long id,
			@AuthMember(required = false) LoginMember loginMember) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.ok(limitedDropService.getDetail(id, memberIdOf(loginMember))));
	}

	private static Long memberIdOf(LoginMember loginMember) {
		return loginMember == null ? null : loginMember.id();
	}
}
