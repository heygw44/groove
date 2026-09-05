package com.groove.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.groove.coupon.entity.Coupon;
import com.groove.inventory.entity.Stock;

class GlobalExceptionHandlerTest {

	GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

	@Nested
	@DisplayName("handleInvalidInput()")
	class HandleInvalidInput {

		@Test
		@DisplayName("필수 요청 파라미터가 누락되면 400 COMMON_INVALID_INPUT 을 반환한다")
		void returnsBadRequestWhenRequestParameterMissing() {
			// given
			MissingServletRequestParameterException exception =
					new MissingServletRequestParameterException("orderAmount", "BigDecimal");

			// when
			ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleInvalidInput(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.COMMON_INVALID_INPUT.name());
		}
	}

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
		@DisplayName("Hibernate 가 엔티티명만 실어 보내도 재고 충돌이면 409 STOCK_CONFLICT 를 반환한다")
		void returnsStockConflictWhenStockEntityNameGiven() {
			// given: Hibernate 는 Class 가 아니라 엔티티명 문자열을 싣는다. 프로덕션에서 실제로 오는 형태다.
			ObjectOptimisticLockingFailureException exception =
					new ObjectOptimisticLockingFailureException(Stock.class.getName(), 1L);

			// when
			ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleOptimisticLock(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.STOCK_CONFLICT.name());
		}

		@Test
		@DisplayName("Class 로 만들어진 재고 충돌도 409 STOCK_CONFLICT 를 반환한다")
		void returnsStockConflictWhenStockClassGiven() {
			// given
			ObjectOptimisticLockingFailureException exception =
					new ObjectOptimisticLockingFailureException(Stock.class, 1L);

			// when
			ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleOptimisticLock(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.STOCK_CONFLICT.name());
		}

		@Test
		@DisplayName("재고가 아닌 엔티티의 충돌이면 409 COMMON_CONFLICT 를 반환한다")
		void returnsCommonConflictWhenOtherEntity() {
			// given: 관리자 쿠폰 수정 경합이 이 경로를 탄다.
			ObjectOptimisticLockingFailureException exception =
					new ObjectOptimisticLockingFailureException(Coupon.class.getName(), 1L);

			// when
			ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleOptimisticLock(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.COMMON_CONFLICT.name());
		}

		@Test
		@DisplayName("엔티티 정보가 없으면 409 COMMON_CONFLICT 를 반환한다")
		void returnsCommonConflictWhenEntityUnknown() {
			// given: 배치 UPDATE 행 수 불일치는 엔티티 정보 없이 온다.
			ObjectOptimisticLockingFailureException exception = new ObjectOptimisticLockingFailureException(
					"batch update returned unexpected row count", new RuntimeException());

			// when
			ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleOptimisticLock(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.COMMON_CONFLICT.name());
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
		@DisplayName("비관적 락 대기에 실패하면 409 COMMON_CONFLICT 를 반환한다")
		void returnsCommonConflict() {
			// given: 이 예외는 어떤 엔티티였는지 담지 않으므로 도메인을 단정하지 않는다.
			PessimisticLockingFailureException exception =
					new PessimisticLockingFailureException("lock wait timeout");

			// when
			ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handlePessimisticLock(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.COMMON_CONFLICT.name());
		}

		@Test
		@DisplayName("데드락 희생자도 같은 핸들러를 타 409 COMMON_CONFLICT 를 반환한다")
		void returnsCommonConflictOnDeadlock() {
			// given
			CannotAcquireLockException exception = new CannotAcquireLockException("deadlock found");

			// when
			ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handlePessimisticLock(exception);

			// then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.COMMON_CONFLICT.name());
		}
	}
}
