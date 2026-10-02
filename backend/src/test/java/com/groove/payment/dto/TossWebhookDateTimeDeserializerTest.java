package com.groove.payment.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

class TossWebhookDateTimeDeserializerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Nested
	@DisplayName("deserialize()")
	class Deserialize {

		@ParameterizedTest
		@CsvSource({
			"2022-01-01T00:00:00.000000, 2022-01-01T00:00:00+09:00",
			"2022-01-01T00:00:00.123456, 2022-01-01T00:00:00.123456+09:00",
			"2022-01-01T00:00:00, 2022-01-01T00:00:00+09:00",
			"2026-09-22T10:00:00+09:00, 2026-09-22T10:00:00+09:00",
			"2026-09-22T01:00:00.5Z, 2026-09-22T01:00:00.5Z"
		})
		@DisplayName("오프셋이 있으면 그대로, 없으면 Asia/Seoul 로 해석한다")
		void parsesWithAndWithoutOffset(String createdAt, String expected) throws Exception {
			// when
			PaymentDepositCallbackRequest request = objectMapper.readValue(
					"{ \"createdAt\": \"%s\" }".formatted(createdAt), PaymentDepositCallbackRequest.class);

			// then
			assertThat(request.createdAt()).isEqualTo(OffsetDateTime.parse(expected));
		}

		@Test
		@DisplayName("날짜 형식이 아니면 InvalidFormatException 을 던진다")
		void throwsWhenNotDateTime() {
			// given
			String body = "{ \"eventType\": \"PAYMENT_STATUS_CHANGED\", \"createdAt\": \"어제\" }";

			// when & then
			assertThatThrownBy(() -> objectMapper.readValue(body, PaymentWebhookRequest.class))
					.isInstanceOf(InvalidFormatException.class);
		}
	}
}
