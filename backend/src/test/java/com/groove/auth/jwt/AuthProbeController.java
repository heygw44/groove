package com.groove.auth.jwt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;

/** JwtAuthenticationFilter 동작 확인용 테스트 전용 컨트롤러. */
@RestController
public class AuthProbeController {

	@GetMapping("/api/v1/probe/me")
	public ApiResponse<Long> me(@AuthMember LoginMember loginMember) {
		return ApiResponse.ok(loginMember.id());
	}

	@GetMapping("/api/v1/admin/probe")
	public ApiResponse<Void> adminProbe() {
		return ApiResponse.ok();
	}
}
