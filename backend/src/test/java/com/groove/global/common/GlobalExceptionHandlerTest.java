package com.groove.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
}
