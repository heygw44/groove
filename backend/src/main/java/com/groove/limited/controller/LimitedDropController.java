package com.groove.limited.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.limited.dto.LimitedDropDetailResponse;
import com.groove.limited.dto.LimitedDropListResponse;
import com.groove.limited.dto.LimitedPurchaseRequest;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.service.LimitedDropService;
import com.groove.limited.service.LimitedPurchaseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 한정반 공개 조회. serverTime 이 카운트다운 기준 시각으로 실리므로 캐시하지 않는다. */
@Tag(name = "Limited Drop", description = "한정반 조회")
@RestController
@RequestMapping("/api/v1/limited-drops")
@RequiredArgsConstructor
public class LimitedDropController {

	private final LimitedDropService limitedDropService;
	private final LimitedPurchaseService limitedPurchaseService;

	@Operation(summary = "한정반 목록 조회", description = "status 미지정 시 SCHEDULED·OPEN·SOLD_OUT 을 반환, 로그인 시 tasteMatch 포함")
	@SecurityRequirements
	@GetMapping
	public ResponseEntity<ApiResponse<LimitedDropListResponse>> getList(
			@RequestParam(required = false) LimitedDropStatus status,
			@AuthMember(required = false) LoginMember loginMember) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.ok(limitedDropService.getList(status, memberIdOf(loginMember))));
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

	@Operation(summary = "한정반 선착순 구매")
	@PostMapping("/{id}/purchase")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<LimitedPurchaseResponse> purchase(@PathVariable Long id,
			@AuthMember LoginMember loginMember, @Valid @RequestBody LimitedPurchaseRequest request) {
		return ApiResponse.ok(limitedPurchaseService.purchase(id, loginMember.id(), request.addressId()));
	}

	private static Long memberIdOf(LoginMember loginMember) {
		return loginMember == null ? null : loginMember.id();
	}
}
