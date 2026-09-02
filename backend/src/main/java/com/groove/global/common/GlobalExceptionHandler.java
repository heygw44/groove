package com.groove.global.common;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("BusinessException: {} - {}", code.name(), e.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        List<ApiResponse.FieldErrorBody> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiResponse.FieldErrorBody(fe.getField(), resolveMessage(fe)))
                .toList();
        ErrorCode code = ErrorCode.COMMON_VALIDATION_FAILED;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage(), fieldErrors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception e) {
        ErrorCode code = ErrorCode.COMMON_INVALID_INPUT;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        ErrorCode code = ErrorCode.COMMON_METHOD_NOT_ALLOWED;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        ErrorCode code = ErrorCode.COMMON_RESOURCE_NOT_FOUND;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        ErrorCode code = ErrorCode.AUTH_FORBIDDEN;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode code = ErrorCode.COMMON_INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    private String resolveMessage(FieldError fieldError) {
        String message = fieldError.getDefaultMessage();
        return message == null ? "invalid" : message;
    }
}
