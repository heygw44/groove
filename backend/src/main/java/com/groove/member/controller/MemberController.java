package com.groove.member.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.member.dto.MemberResponse;
import com.groove.member.dto.MemberUpdateRequest;
import com.groove.member.dto.PasswordChangeRequest;
import com.groove.member.service.MemberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Member", description = "내 정보")
@RestController
@RequestMapping("/api/v1/members/me")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	@Operation(summary = "내 정보 조회")
	@GetMapping
	public ApiResponse<MemberResponse> getMyInfo(@AuthMember LoginMember loginMember) {
		return ApiResponse.ok(memberService.getMyInfo(loginMember.id()));
	}

	@Operation(summary = "닉네임 변경")
	@PatchMapping
	public ApiResponse<MemberResponse> updateNickname(@AuthMember LoginMember loginMember,
			@Valid @RequestBody MemberUpdateRequest request) {
		return ApiResponse.ok(memberService.updateNickname(loginMember.id(), request));
	}

	@Operation(summary = "비밀번호 변경")
	@PatchMapping("/password")
	public ApiResponse<Void> changePassword(@AuthMember LoginMember loginMember,
			@Valid @RequestBody PasswordChangeRequest request) {
		memberService.changePassword(loginMember.id(), request);
		return ApiResponse.ok();
	}

	@Operation(summary = "회원 탈퇴")
	@DeleteMapping
	public ApiResponse<Void> withdraw(@AuthMember LoginMember loginMember) {
		memberService.withdraw(loginMember.id());
		return ApiResponse.ok();
	}
}
