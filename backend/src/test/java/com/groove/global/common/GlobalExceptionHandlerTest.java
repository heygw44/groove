package com.groove.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
