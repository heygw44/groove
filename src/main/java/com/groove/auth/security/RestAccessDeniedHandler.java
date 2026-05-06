package com.groove.auth.security;

import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ProblemDetailEnricher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 인증은 되었으나 권한이 없는 요청에 대해 ProblemDetail(JSON) 형식으로 403 응답을 반환한다.
 *
 * <p>권한 우회 시도 추적을 위해 WARN 로그를 남긴다.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("auth.forbidden method={} path={} reason={}",
                request.getMethod(),
                request.getRequestURI(),
                accessDeniedException.getClass().getSimpleName());

        ErrorCode errorCode = ErrorCode.AUTH_FORBIDDEN;
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
