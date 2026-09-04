package com.groove.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.config.TossProperties;

class TossPaymentClientTest {

	private static final String BASE_URL = "https://api.tosspayments.com";
	private static final String SECRET_KEY = "test_sk_dummy";
	private static final String PAYMENT_KEY = "tviva20260902abcdef";
	private static final String ORDER_NUMBER = "20260902-K7Q2M9XZ";
	private static final String EXPECTED_AUTHORIZATION = "Basic " + Base64.getEncoder()
			.encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));

	private static final String CONFIRM_RESPONSE = """
			{
				"paymentKey": "tviva20260902abcdef",
				"orderId": "20260902-K7Q2M9XZ",
				"status": "DONE",
				"method": "카드",
				"totalAmount": 75600,
				"requestedAt": "2026-09-02T10:00:59+09:00",
				"approvedAt": "2026-09-02T10:01:12+09:00",
				"cancels": null
			}
			""";

	private static final String CANCEL_RESPONSE = """
			{
				"paymentKey": "tviva20260902abcdef",
				"orderId": "20260902-K7Q2M9XZ",
				"status": "CANCELED",
				"method": "카드",
				"totalAmount": 75600,
				"approvedAt": "2026-09-02T10:01:12+09:00",
				"cancels": [
					{ "cancelReason": "부분 취소", "canceledAt": "2026-09-02T11:00:00+09:00" },
					{ "cancelReason": "고객 변심", "canceledAt": "2026-09-02T11:32:04+09:00" }
				]
			}
			""";

	private static final String ERROR_RESPONSE = """
			{ "code": "REJECT_CARD_COMPANY", "message": "카드사에서 승인을 거절했습니다." }
			""";

	private MockRestServiceServer server;
	private TossPaymentClient tossPaymentClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		tossPaymentClient = new TossPaymentClient(builder.build(), new ObjectMapper(),
				Clock.fixed(Instant.parse("2026-09-02T01:00:00Z"), ZoneId.of("Asia/Seoul")),
				new TossProperties("test_ck_dummy", SECRET_KEY, BASE_URL));
	}

	@AfterEach
	void tearDown() {
		server.verify();
	}

	@Nested
	@DisplayName("confirm()")
	class Confirm {

		@Test
		@DisplayName("승인하면 Basic 인증 헤더와 원 단위 정수 금액을 보내고 응답을 매핑한다")
		void sendsBasicAuthWithIntegerAmountAndMapsResponse() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andExpect(method(HttpMethod.POST))
					.andExpect(header(HttpHeaders.AUTHORIZATION, EXPECTED_AUTHORIZATION))
					.andExpect(jsonPath("$.paymentKey").value(PAYMENT_KEY))
					.andExpect(jsonPath("$.orderId").value(ORDER_NUMBER))
					.andExpect(jsonPath("$.amount").value(75600))
					.andRespond(withSuccess(CONFIRM_RESPONSE, MediaType.APPLICATION_JSON));

			// when
			PaymentConfirmResult result = tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER,
					new BigDecimal("75600.00"));

			// then
			assertThat(result.paymentKey()).isEqualTo(PAYMENT_KEY);
			assertThat(result.orderId()).isEqualTo(ORDER_NUMBER);
			assertThat(result.method()).isEqualTo("카드");
			assertThat(result.totalAmount()).isEqualByComparingTo("75600");
			assertThat(result.approvedAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 10, 1, 12));
		}

		@Test
		@DisplayName("토스가 오류를 응답하면 PAYMENT_CONFIRM_FAILED 로 바꾸고 토스 코드는 예외 상세에만 남긴다")
		void translatesTossErrorToConfirmFailed() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withBadRequest().body(ERROR_RESPONSE).contentType(MediaType.APPLICATION_JSON));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER, new BigDecimal("75600")))
					.isInstanceOf(BusinessException.class)
					.hasMessageContaining("REJECT_CARD_COMPANY")
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_CONFIRM_FAILED);
		}

		@Test
		@DisplayName("에러 본문이 JSON 이 아니어도 PAYMENT_CONFIRM_FAILED 로 떨어진다")
		void translatesUnparsableErrorBodyToConfirmFailed() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withServerError().body("<html>Bad Gateway</html>"));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER, new BigDecimal("75600")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_CONFIRM_FAILED);
		}
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("취소하면 paymentKey 경로로 요청하고 마지막 취소 시각을 매핑한다")
		void sendsCancelReasonAndMapsLastCanceledAt() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY + "/cancel"))
					.andExpect(method(HttpMethod.POST))
					.andExpect(header(HttpHeaders.AUTHORIZATION, EXPECTED_AUTHORIZATION))
					.andExpect(jsonPath("$.cancelReason").value("고객 변심"))
					.andRespond(withSuccess(CANCEL_RESPONSE, MediaType.APPLICATION_JSON));

			// when
			PaymentCancelResult result = tossPaymentClient.cancel(PAYMENT_KEY, "고객 변심");

			// then
			assertThat(result.paymentKey()).isEqualTo(PAYMENT_KEY);
			assertThat(result.status()).isEqualTo("CANCELED");
			assertThat(result.canceledAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 11, 32, 4));
		}

		@Test
		@DisplayName("토스가 오류를 응답하면 PAYMENT_CANCEL_FAILED 로 바꾼다")
		void translatesTossErrorToCancelFailed() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY + "/cancel"))
					.andRespond(withBadRequest().body(ERROR_RESPONSE).contentType(MediaType.APPLICATION_JSON));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.cancel(PAYMENT_KEY, "고객 변심"))
					.isInstanceOf(BusinessException.class)
					.hasMessageContaining("REJECT_CARD_COMPANY")
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_CANCEL_FAILED);
		}
	}
}
