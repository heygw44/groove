package com.groove.global.common;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
		ErrorCode code = ex.getErrorCode();
		log.warn("BusinessException: {} - {}", code.name(), ex.getMessage());
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
		List<ApiResponse.FieldErrorBody> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> new ApiResponse.FieldErrorBody(fe.getField(), resolveMessage(fe)))
				.toList();
		ErrorCode code = ErrorCode.COMMON_VALIDATION_FAILED;
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), code.getMessage(), fieldErrors));
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception ex) {
		ErrorCode code = ErrorCode.COMMON_INVALID_INPUT;
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), code.getMessage()));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
		ErrorCode code = ErrorCode.COMMON_METHOD_NOT_ALLOWED;
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), code.getMessage()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
		ErrorCode code = ErrorCode.COMMON_RESOURCE_NOT_FOUND;
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), code.getMessage()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
		ErrorCode code = ErrorCode.AUTH_FORBIDDEN;
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), code.getMessage()));
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		log.warn("ObjectOptimisticLockingFailureException: {}", ex.getMessage());
		ErrorCode code = ErrorCode.STOCK_CONFLICT;
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), code.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		ErrorCode code = ErrorCode.COMMON_INTERNAL_ERROR;
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), code.getMessage()));
	}

	private String resolveMessage(FieldError fieldError) {
		String message = fieldError.getDefaultMessage();
		return message == null ? "invalid" : message;
	}
}
