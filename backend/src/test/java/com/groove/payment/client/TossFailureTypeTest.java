package com.groove.payment.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatusCode;

class TossFailureTypeTest {

	@Nested
	@DisplayName("classify()")
	class Classify {

		@ParameterizedTest
		@CsvSource({"400", "500"})
		@DisplayName("ALREADY_PROCESSED_PAYMENT 코드면 상태 코드와 무관하게 ALREADY_PROCESSED 로 분류한다")
		void classifiesAlreadyProcessedRegardlessOfStatus(int status) {
			// when
			TossFailureType actual = TossFailureType.classify(HttpStatusCode.valueOf(status),
					"ALREADY_PROCESSED_PAYMENT");

			// then
			assertThat(actual).isEqualTo(TossFailureType.ALREADY_PROCESSED);
		}

		@ParameterizedTest
		@CsvSource({"500,", "503,REJECT_CARD_COMPANY", "500,FAILED_INTERNAL_SYSTEM_PROCESSING"})
		@DisplayName("5xx 면 토스 코드와 무관하게 RESULT_UNKNOWN 으로 분류한다")
		void classifiesResultUnknownFor5xxRegardlessOfCode(int status, String tossCode) {
			// when
			TossFailureType actual = TossFailureType.classify(HttpStatusCode.valueOf(status), tossCode);

			// then
			assertThat(actual).isEqualTo(TossFailureType.RESULT_UNKNOWN);
		}

		@ParameterizedTest
		@CsvSource({"400", "404"})
		@DisplayName("4xx 인데 토스 코드가 없으면 RESULT_UNKNOWN 으로 분류한다")
		void classifiesResultUnknownWhenCodeMissing(int status) {
			// when
			TossFailureType actual = TossFailureType.classify(HttpStatusCode.valueOf(status), null);

			// then
			assertThat(actual).isEqualTo(TossFailureType.RESULT_UNKNOWN);
		}

		@ParameterizedTest
		@CsvSource({"400,PROVIDER_ERROR", "409,IDEMPOTENT_REQUEST_PROCESSING", "403,FORBIDDEN_CONSECUTIVE_REQUEST"})
		@DisplayName("토스가 처리 여부를 단정할 수 없는 4xx 코드면 RESULT_UNKNOWN 으로 분류한다")
		void classifiesResultUnknownForAmbiguousClientErrorCodes(int status, String tossCode) {
			// when
			TossFailureType actual = TossFailureType.classify(HttpStatusCode.valueOf(status), tossCode);

			// then
			assertThat(actual).isEqualTo(TossFailureType.RESULT_UNKNOWN);
		}

		@ParameterizedTest
		@CsvSource({"400,REJECT_CARD_COMPANY", "403,INVALID_API_KEY", "404,NOT_FOUND_PAYMENT"})
		@DisplayName("그 밖의 4xx 코드는 REJECTED 로 분류한다")
		void classifiesRejectedForOtherClientErrorCodes(int status, String tossCode) {
			// when
			TossFailureType actual = TossFailureType.classify(HttpStatusCode.valueOf(status), tossCode);

			// then
			assertThat(actual).isEqualTo(TossFailureType.REJECTED);
		}
	}
}
