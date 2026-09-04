package com.groove.global.common;

import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.groove.inventory.entity.Stock;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/** MySQL 이 유니크 제약 위반에 쓰는 에러 코드. 같은 SQLException 계열의 FK·NOT NULL 위반과 갈라내는 기준. */
	private static final int MYSQL_DUPLICATE_ENTRY = 1062;

	/** 낙관적 락 실패에서 재고를 특정하는 기준. Hibernate 는 클래스가 아닌 엔티티명만 실어 보내므로 이름으로 비교한다. */
	private static final String STOCK_ENTITY_NAME = Stock.class.getName();

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

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
		MissingServletRequestPartException.class})
	public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception ex) {
		ErrorCode code = ErrorCode.COMMON_INVALID_INPUT;
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), code.getMessage()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
		log.warn("MaxUploadSizeExceededException: {}", ex.getMessage());
		ErrorCode code = ErrorCode.FILE_SIZE_EXCEEDED;
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
		log.warn("ObjectOptimisticLockingFailureException({}): {}", ex.getPersistentClassName(), ex.getMessage());
		return conflict(STOCK_ENTITY_NAME.equals(ex.getPersistentClassName())
				? ErrorCode.STOCK_CONFLICT
				: ErrorCode.COMMON_CONFLICT);
	}

	// 락 대기 실패 예외는 어떤 엔티티였는지 담지 않는다. 도메인 단정은 락을 건 서비스가 하고 여기서는 일반 충돌로만 답한다.
	@ExceptionHandler(PessimisticLockingFailureException.class)
	public ResponseEntity<ApiResponse<Void>> handlePessimisticLock(PessimisticLockingFailureException ex) {
		log.warn("PessimisticLockingFailureException: {}", ex.getMessage());
		return conflict(ErrorCode.COMMON_CONFLICT);
	}

	/**
	 * 서비스 계층이 도메인 코드로 변환하지 못하고 새어 나온 제약 위반의 안전망.
	 * 중복키만 재시도로 풀릴 수 있는 경합으로 보고 409 로 낮춘다. NOT NULL·FK·길이 초과는
	 * 사용자가 다시 시도해도 달라지지 않는 서버 결함이라 500 경로에 그대로 남겨 로그와 알림에 걸리게 한다.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		if (!isDuplicateKey(ex)) {
			return handleUnexpected(ex);
		}
		log.warn("DataIntegrityViolationException(duplicate key): {}", ex.getMostSpecificCause().getMessage());
		return conflict(ErrorCode.COMMON_CONFLICT);
	}

	private boolean isDuplicateKey(DataIntegrityViolationException ex) {
		return ex.getMostSpecificCause() instanceof SQLException cause
				&& cause.getErrorCode() == MYSQL_DUPLICATE_ENTRY;
	}

	private ResponseEntity<ApiResponse<Void>> conflict(ErrorCode code) {
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
