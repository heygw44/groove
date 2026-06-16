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
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    /**
     * 메서드 파라미터 제약 위반(@Validated)을 처리한다.
     * RFC 7807 확장 필드 violations 에 위반 필드 경로/메시지 배열을 담는다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(), errorCode.getDefaultMessage());
        pd.setProperty("code", errorCode.getCode());
        List<Map<String, String>> violations = ex.getConstraintViolations().stream()
                .map(v -> Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", v.getMessage()))
                .toList();
        pd.setProperty("violations", violations);
        ProblemDetailEnricher.enrich(pd, errorCode.getStatus().value());
        return ResponseEntity.status(errorCode.getStatus()).body(pd);
    }

    /**
     * 본문 검증 위반(@RequestBody @Valid)을 처리한다. RFC 7807 확장 필드 violations(필드 경로/메시지)를 담는다.
     * 필드 단위 위반은 FieldError 로 field=프로퍼티 경로, 클래스 레벨 제약은 ObjectError 로 field 를 비운다.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(), errorCode.getDefaultMessage());
        pd.setProperty("code", errorCode.getCode());

        List<Map<String, String>> violations = new ArrayList<>(ex.getBindingResult().getErrorCount());
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            violations.add(Map.of(
                    "field", fe.getField(),
                    "message", messageOrDefault(fe.getDefaultMessage())));
        }
        for (ObjectError oe : ex.getBindingResult().getGlobalErrors()) {
            violations.add(Map.of(
                    "field", "",
                    "message", messageOrDefault(oe.getDefaultMessage())));
        }
        pd.setProperty("violations", violations);

        ProblemDetailEnricher.enrich(pd, errorCode.getStatus().value());
        return ResponseEntity.status(errorCode.getStatus()).body(pd);
    }

    /** 검증 메시지가 null 이면 기본 메시지를 반환한다. */
    private static String messageOrDefault(String message) {
        return message != null ? message : "유효하지 않은 값입니다";
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("처리되지 않은 예외 [{}]", request.getRequestURI(), ex);
        return buildResponse(ErrorCode.INTERNAL_ERROR);
    }

    /**
     * Spring 내장 예외와 BusinessException 의 ProblemDetail 에 code/timestamp/traceId 확장 필드를 일괄 주입한다.
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
