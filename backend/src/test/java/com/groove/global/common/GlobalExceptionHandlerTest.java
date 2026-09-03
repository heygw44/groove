package com.groove.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.groove.inventory.entity.Stock;

class GlobalExceptionHandlerTest {

	GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

	@Nested
	@DisplayName("handleMaxUploadSizeExceeded()")
	class HandleMaxUploadSizeExceeded {

		@Test
		@DisplayName("업로드 용량 초과 예외면 400 FILE_SIZE_EXCEEDED 를 반환한다")
		void returnsFileSizeExceeded() {
			// given
			MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(5 * 1024 * 1024L);

			// when
			ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleMaxUploadSizeExceeded(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED.name());
		}
	}

	@Nested
	@DisplayName("handleOptimisticLock()")
	class HandleOptimisticLock {

		@Test
		@DisplayName("낙관적 락 충돌이면 409 STOCK_CONFLICT 를 반환한다")
		void returnsStockConflict() {
			// given
			ObjectOptimisticLockingFailureException exception =
					new ObjectOptimisticLockingFailureException(Stock.class, 1L);

			// when
			ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleOptimisticLock(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.STOCK_CONFLICT.name());
		}
	}

	@Nested
	@DisplayName("handleDataIntegrityViolation()")
	class HandleDataIntegrityViolation {

		@Test
		@DisplayName("중복키 위반이면 409 COMMON_CONFLICT 를 반환한다")
		void returnsConflictOnDuplicateKey() {
			// given
			DataIntegrityViolationException exception = new DataIntegrityViolationException("insert failed",
					new SQLException("Duplicate entry 'x' for key 'uk_x'", "23000", 1062));

			// when
			ResponseEntity<ApiResponse<Void>> response =
					globalExceptionHandler.handleDataIntegrityViolation(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.COMMON_CONFLICT.name());
		}

		@Test
		@DisplayName("중복키가 아닌 제약 위반이면 500 을 유지해 서버 결함이 묻히지 않게 한다")
		void keepsInternalErrorOnOtherViolation() {
			// given: FK 위반. 재시도해도 달라지지 않으므로 409 로 낮추면 안 된다.
			DataIntegrityViolationException exception = new DataIntegrityViolationException("insert failed",
					new SQLException("Cannot add or update a child row", "23000", 1452));

			// when
			ResponseEntity<ApiResponse<Void>> response =
					globalExceptionHandler.handleDataIntegrityViolation(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.COMMON_INTERNAL_ERROR.name());
		}

		@Test
		@DisplayName("SQL 원인 예외가 없으면 500 을 유지한다")
		void keepsInternalErrorWithoutSqlCause() {
			// given
			DataIntegrityViolationException exception = new DataIntegrityViolationException("constraint violated");

			// when
			ResponseEntity<ApiResponse<Void>> response =
					globalExceptionHandler.handleDataIntegrityViolation(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.COMMON_INTERNAL_ERROR.name());
		}
	}

	@Nested
	@DisplayName("handlePessimisticLock()")
	class HandlePessimisticLock {

		@Test
		@DisplayName("비관적 락 대기에 실패하면 409 STOCK_CONFLICT 를 반환한다")
		void returnsStockConflict() {
			// given
			PessimisticLockingFailureException exception =
					new PessimisticLockingFailureException("lock wait timeout");

			// when
			ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handlePessimisticLock(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.STOCK_CONFLICT.name());
		}
	}
}
