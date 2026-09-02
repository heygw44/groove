package com.groove.global.config;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 인증되지 않은 요청 → 401 + 공통 에러 응답 (Security 필터 단계라 @RestControllerAdvice가 잡지 못함). */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	/** JwtAuthenticationFilter 가 토큰 검증 실패 원인을 담아두는 request attribute 키. */
	public static final String ERROR_CODE_ATTRIBUTE = "com.groove.auth.errorCode";

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException authException) throws IOException {
		ErrorCode code = resolveErrorCode(request);
		response.setStatus(code.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), ApiResponse.error(code.name(), code.getMessage()));
	}

	private ErrorCode resolveErrorCode(HttpServletRequest request) {
		Object attribute = request.getAttribute(ERROR_CODE_ATTRIBUTE);
		if (attribute instanceof ErrorCode errorCode) {
			return errorCode;
		}
		return ErrorCode.AUTH_UNAUTHORIZED;
	}
}
