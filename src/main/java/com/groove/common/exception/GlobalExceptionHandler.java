package com.groove.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return buildResponse(ErrorCode.AUTH_UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(ErrorCode.AUTH_FORBIDDEN);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        return buildResponse(ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("처리되지 않은 예외 [{}]", request.getRequestURI(), ex);
        return buildResponse(ErrorCode.INTERNAL_ERROR);
    }

    /**
     * Spring 내장 예외 + BusinessException(ErrorResponseException) 모두 이 지점을 통과.
     * code/timestamp/traceId 확장 필드를 일괄 주입한다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);

        if (response.getBody() instanceof ProblemDetail pd) {
            ProblemDetailEnricher.enrich(pd, statusCode.value());
        }

        return response;
    }

    private ResponseEntity<ProblemDetail> buildResponse(ErrorCode errorCode) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(), errorCode.getDefaultMessage());
        pd.setProperty("code", errorCode.getCode());
        ProblemDetailEnricher.enrich(pd, errorCode.getStatus().value());
        return ResponseEntity.status(errorCode.getStatus()).body(pd);
    }
}
