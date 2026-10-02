package com.groove.auth.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Auth", description = "회원가입/로그인/토큰")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	static final String CLIENT_IDLE_HEADER = "X-Client-Idle-Seconds";
	private static final long MAX_CLIENT_IDLE_SECONDS = 86_400L;

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
			@CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken,
			@Parameter(description = "마지막 사용자 입력 이후 경과 초(탭 전체 기준). 관리자 유휴 만료 판정에만 쓴다.")
			@RequestHeader(name = CLIENT_IDLE_HEADER, required = false) String clientIdleSeconds) {
		try {
			AuthTokens tokens = authService.reissue(refreshToken, parseClientIdle(clientIdleSeconds));
			return tokenResponse(tokens);
		} catch (BusinessException e) {
			// 재발급이 던지는 예외는 전부 되살릴 수 없는 상태(토큰 없음·위조·만료, 세션 없음·재사용·절대 만료,
			// 회원 정지·탈퇴)라 죽은 쿠키를 지운다. Redis 장애 같은 BusinessException 이 아닌 예외는 잡지 않아
			// 500 이 나가고 쿠키는 유지된다.
			ErrorCode code = e.getErrorCode();
			log.warn("BusinessException: {} - {}", code.name(), e.getMessage());
			return ResponseEntity.status(code.getStatus())
					.header(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString())
					.body(ApiResponse.error(code.name(), code.getMessage()));
		}
	}

	@Operation(summary = "로그아웃")
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(@AuthMember LoginMember loginMember,
			@CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
		authService.logout(loginMember.id(), refreshToken);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString())
				.body(ApiResponse.ok());
	}

	private static Duration parseClientIdle(String value) {
		if (value == null) {
			return Duration.ZERO;
		}
		try {
			long seconds = Long.parseLong(value.trim());
			return Duration.ofSeconds(Math.min(Math.max(seconds, 0L), MAX_CLIENT_IDLE_SECONDS));
		} catch (NumberFormatException e) {
			return Duration.ZERO;
		}
	}

	private ResponseEntity<ApiResponse<TokenResponse>> tokenResponse(AuthTokens tokens) {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE,
						cookieFactory.create(tokens.refreshToken(), tokens.refreshTokenMaxAge()).toString())
				.body(ApiResponse.ok(TokenResponse.from(tokens)));
	}
}
