package com.groove.limited.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;
import com.groove.limited.dto.AdminLimitedDropResponse;
import com.groove.limited.dto.AdminLimitedDropSummaryResponse;
import com.groove.limited.dto.LimitedDropCreateRequest;
import com.groove.limited.dto.LimitedDropUpdateRequest;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.service.AdminLimitedDropService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Limited Drop", description = "관리자 한정반 드롭 관리")
@RestController
@RequestMapping("/api/v1/admin/limited-drops")
@RequiredArgsConstructor
public class AdminLimitedDropController {

	private final AdminLimitedDropService adminLimitedDropService;

	@Operation(summary = "한정반 드롭 등록")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AdminLimitedDropResponse> create(@AuthMember LoginMember loginMember,
			@Valid @RequestBody LimitedDropCreateRequest request) {
		return ApiResponse.ok(adminLimitedDropService.create(loginMember.id(), request));
	}

	@Operation(summary = "한정반 드롭 목록 조회")
	@GetMapping
	public ApiResponse<PageResponse<AdminLimitedDropSummaryResponse>> getList(
			@RequestParam(required = false) LimitedDropStatus status,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(adminLimitedDropService.getList(status, pageable));
	}

	@Operation(summary = "한정반 드롭 수정")
	@PatchMapping("/{id}")
	public ApiResponse<AdminLimitedDropResponse> update(@AuthMember LoginMember loginMember, @PathVariable Long id,
			@Valid @RequestBody LimitedDropUpdateRequest request) {
		return ApiResponse.ok(adminLimitedDropService.update(loginMember.id(), id, request));
	}

	@Operation(summary = "한정반 드롭 강제 오픈")
	@PatchMapping("/{id}/open")
	public ApiResponse<AdminLimitedDropResponse> open(@AuthMember LoginMember loginMember, @PathVariable Long id) {
		return ApiResponse.ok(adminLimitedDropService.open(loginMember.id(), id));
	}

	@Operation(summary = "한정반 드롭 강제 마감")
	@PatchMapping("/{id}/close")
	public ApiResponse<AdminLimitedDropResponse> close(@AuthMember LoginMember loginMember, @PathVariable Long id) {
		return ApiResponse.ok(adminLimitedDropService.close(loginMember.id(), id));
	}
}
