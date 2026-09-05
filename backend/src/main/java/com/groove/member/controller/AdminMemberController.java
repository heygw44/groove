package com.groove.member.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;
import com.groove.member.dto.AdminMemberDetailResponse;
import com.groove.member.dto.AdminMemberSearchRequest;
import com.groove.member.dto.AdminMemberStatusChangeRequest;
import com.groove.member.dto.AdminMemberSummaryResponse;
import com.groove.member.service.AdminMemberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Member", description = "관리자 회원 관리")
@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

	private final AdminMemberService adminMemberService;

	@Operation(summary = "관리자 회원 목록 조회")
	@GetMapping
	public ApiResponse<PageResponse<AdminMemberSummaryResponse>> getList(
			@Valid @ModelAttribute AdminMemberSearchRequest request) {
		return ApiResponse.ok(adminMemberService.getList(request));
	}

	@Operation(summary = "관리자 회원 상세 조회")
	@GetMapping("/{id}")
	public ApiResponse<AdminMemberDetailResponse> getDetail(@PathVariable Long id) {
		return ApiResponse.ok(adminMemberService.getDetail(id));
	}

	@Operation(summary = "회원 상태 변경(정지/활성)")
	@PatchMapping("/{id}/status")
	public ApiResponse<AdminMemberDetailResponse> changeStatus(@AuthMember LoginMember admin, @PathVariable Long id,
			@Valid @RequestBody AdminMemberStatusChangeRequest request) {
		return ApiResponse.ok(adminMemberService.changeStatus(admin.id(), id, request));
	}
}
