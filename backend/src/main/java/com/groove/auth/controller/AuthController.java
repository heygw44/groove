package com.groove.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.cookie.RefreshTokenCookieFactory;
import com.groove.auth.dto.AuthTokens;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.dto.SignupResponse;
import com.groove.auth.dto.TokenResponse;
import com.groove.auth.resolver.AuthMember;
import com.groove.auth.service.AuthService;
import com.groove.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth", description = "회원가입/로그인/토큰")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final RefreshTokenCookieFactory cookieFactory;

	@Operation(summary = "회원가입")
	@SecurityRequirements
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ApiResponse.ok(authService.signup(request));
	}

	@Operation(summary = "로그인")
	@SecurityRequirements
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
		AuthTokens tokens = authService.login(request);
		return tokenResponse(tokens);
	}

	@Operation(summary = "Access Token 재발급")
	@SecurityRequirements
	@PostMapping("/reissue")
	public ResponseEntity<ApiResponse<TokenResponse>> reissue(
			@CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
		AuthTokens tokens = authService.reissue(refreshToken);
		return tokenResponse(tokens);
	}

	@Operation(summary = "로그아웃")
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(@AuthMember LoginMember loginMember) {
		authService.logout(loginMember.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString())
				.body(ApiResponse.ok());
	}

	private ResponseEntity<ApiResponse<TokenResponse>> tokenResponse(AuthTokens tokens) {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookieFactory.create(tokens.refreshToken()).toString())
				.body(ApiResponse.ok(TokenResponse.from(tokens)));
	}
}
