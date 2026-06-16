package com.groove.auth.security;

import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ProblemDetailEnricher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 인증되지 않은 요청에 ProblemDetail(JSON) 형식으로 401 응답을 반환하고, 클래스명과
 * 요청 경로만 WARN 로그로 남긴다.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("auth.failure method={} path={} reason={}",
                request.getMethod(),
                request.getRequestURI(),
                authException.getClass().getSimpleName());

        ErrorCode errorCode = ErrorCode.AUTH_UNAUTHORIZED;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(), errorCode.getDefaultMessage());
        problem.setTitle(errorCode.getStatus().getReasonPhrase());
        problem.setProperty("code", errorCode.getCode());
        ProblemDetailEnricher.enrich(problem, errorCode.getStatus().value());

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
