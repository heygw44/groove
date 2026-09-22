package com.groove.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
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

	private static final String CONFIRM_RESPONSE_WITHOUT_APPROVED_AT = """
			{
				"paymentKey": "tviva20260902abcdef",
				"orderId": "20260902-K7Q2M9XZ",
				"status": "DONE",
				"method": "카드",
				"totalAmount": 75600,
				"requestedAt": "2026-09-02T10:00:59+09:00",
				"approvedAt": null,
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

	private static final String CANCEL_RESPONSE_WITHOUT_CANCELS = """
			{
				"paymentKey": "tviva20260902abcdef",
				"orderId": "20260902-K7Q2M9XZ",
				"status": "CANCELED",
				"method": "카드",
				"totalAmount": 75600,
				"approvedAt": "2026-09-02T10:01:12+09:00",
				"cancels": null
			}
			""";

	private static final String LOOKUP_RESPONSE_DONE_OTHER_PAYMENT_KEY = """
			{
				"paymentKey": "tviva20260902ffffff",
				"orderId": "20260902-K7Q2M9XZ",
				"status": "DONE",
				"method": "카드",
				"totalAmount": 75600,
				"approvedAt": "2026-09-02T10:01:12+09:00",
				"cancels": null
			}
			""";

	private static final String LOOKUP_RESPONSE_DONE_WITHOUT_TOTAL_AMOUNT = """
			{
				"paymentKey": "tviva20260902abcdef",
				"orderId": "20260902-K7Q2M9XZ",
				"status": "DONE",
				"method": "카드",
				"totalAmount": null,
				"approvedAt": "2026-09-02T10:01:12+09:00",
				"cancels": null
			}
			""";

	private static final String LOOKUP_RESPONSE_UNKNOWN_STATUS = """
			{
				"paymentKey": "tviva20260902abcdef",
				"orderId": "20260902-K7Q2M9XZ",
				"status": "SOME_FUTURE_STATUS",
				"method": "카드",
				"totalAmount": 75600,
				"approvedAt": "2026-09-02T10:01:12+09:00",
				"cancels": null
			}
			""";

	private static final String ERROR_RESPONSE = """
			{ "code": "REJECT_CARD_COMPANY", "message": "카드사에서 승인을 거절했습니다." }
			""";

	private static final String ERROR_RESPONSE_WITHOUT_CODE = """
			{ "message": "코드 필드가 없는 에러 응답" }
			""";

	private static final String ERROR_RESPONSE_ALREADY_PROCESSED = """
			{ "code": "ALREADY_PROCESSED_PAYMENT", "message": "이미 처리된 결제 요청입니다." }
			""";

	private static final String ERROR_RESPONSE_NOT_FOUND_PAYMENT = """
			{ "code": "NOT_FOUND_PAYMENT", "message": "존재하지 않는 결제 정보입니다." }
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

	private static Stream<Arguments> ambiguousTossFailures() {
		return Stream.of(
				Arguments.of(HttpStatus.BAD_REQUEST, "PROVIDER_ERROR", "일시적인 오류가 발생했습니다."),
				Arguments.of(HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING", "같은 멱등키 요청을 처리 중입니다."),
				Arguments.of(HttpStatus.FORBIDDEN, "FORBIDDEN_CONSECUTIVE_REQUEST", "같은 요청이 반복되고 있습니다."),
				Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, "FAILED_INTERNAL_SYSTEM_PROCESSING", "일시적인 시스템 오류입니다."));
	}

	private static String tossError(String code, String message) {
		return "{ \"code\": \"" + code + "\", \"message\": \"" + message + "\" }";
	}

	@Nested
	@DisplayName("confirm()")
	class Confirm {

		@Test
		@DisplayName("승인하면 Basic 인증과 멱등키 헤더, 원 단위 정수 금액을 보내고 응답을 매핑한다")
		void sendsBasicAuthWithIdempotencyKeyAndMapsResponse() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andExpect(method(HttpMethod.POST))
					.andExpect(header(HttpHeaders.AUTHORIZATION, EXPECTED_AUTHORIZATION))
					.andExpect(header("Idempotency-Key", "confirm-" + PAYMENT_KEY))
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
		@DisplayName("approvedAt 이 없으면 승인 시각은 null 로 매핑한다")
		void mapsNullApprovedAtWhenMissing() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withSuccess(CONFIRM_RESPONSE_WITHOUT_APPROVED_AT, MediaType.APPLICATION_JSON));

			// when
			PaymentConfirmResult result = tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER,
					new BigDecimal("75600"));

			// then
			assertThat(result.approvedAt()).isNull();
		}

		@Test
		@DisplayName("응답이 2xx 인데 본문이 비어 있으면 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenResponseBodyMissing() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withNoContent());

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER, new BigDecimal("75600")))
					.isInstanceOf(BusinessException.class)
					.hasMessageContaining("TOSS 응답 본문이 비어 있습니다")
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}

		@Test
		@DisplayName("읽기 타임아웃이면 토스가 처리했는지 알 수 없어 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenReadTimesOut() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withException(new SocketTimeoutException("Read timed out")));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER, new BigDecimal("75600")))
					.isInstanceOf(BusinessException.class)
					.hasMessageContaining("TOSS 통신 실패")
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}

		@Test
		@DisplayName("토스가 명확히 거절하면 PAYMENT_CONFIRM_FAILED 로 바꾸고 토스 코드는 예외 상세에만 남긴다")
		void throwsConfirmFailedWhenTossRejects() {
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

		@ParameterizedTest(name = "{1} ({0}) → PAYMENT_RESULT_UNKNOWN")
		@MethodSource("com.groove.payment.client.TossPaymentClientTest#ambiguousTossFailures")
		@DisplayName("토스가 처리 여부를 단정할 수 없는 응답을 주면 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownForAmbiguousTossFailures(HttpStatus status, String tossCode, String message) {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withStatus(status).body(tossError(tossCode, message))
							.contentType(MediaType.APPLICATION_JSON));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER, new BigDecimal("75600")))
					.isInstanceOf(BusinessException.class)
					.hasMessageContaining(tossCode)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}

		@Test
		@DisplayName("에러 응답 바디가 비어 있으면 코드를 알 수 없어 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenErrorBodyBlank() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withBadRequest().body(""));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER, new BigDecimal("75600")))
					.isInstanceOf(BusinessException.class)
					.hasMessageContaining("UNKNOWN")
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}

		@Test
		@DisplayName("에러 응답에 code 필드가 없으면 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenErrorCodeFieldMissing() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withBadRequest().body(ERROR_RESPONSE_WITHOUT_CODE)
							.contentType(MediaType.APPLICATION_JSON));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER, new BigDecimal("75600")))
					.isInstanceOf(BusinessException.class)
					.hasMessageContaining("UNKNOWN")
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}

		@Test
		@DisplayName("에러 본문이 JSON 이 아니어도 5xx 면 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenServerErrorBodyUnparsable() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withServerError().body("<html>Bad Gateway</html>"));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER, new BigDecimal("75600")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}

		@Test
		@DisplayName("이미 처리된 승인 요청이면 조회로 확인해 키와 금액이 같으면 성공으로 이어 붙인다")
		void absorbsAlreadyProcessedWhenLookupMatches() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withBadRequest().body(ERROR_RESPONSE_ALREADY_PROCESSED)
							.contentType(MediaType.APPLICATION_JSON));
			server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + ORDER_NUMBER))
					.andExpect(method(HttpMethod.GET))
					.andExpect(header(HttpHeaders.AUTHORIZATION, EXPECTED_AUTHORIZATION))
					.andExpect(headerDoesNotExist("Idempotency-Key"))
					.andRespond(withSuccess(CONFIRM_RESPONSE, MediaType.APPLICATION_JSON));

			// when
			PaymentConfirmResult result = tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER,
					new BigDecimal("75600"));

			// then
			assertThat(result.paymentKey()).isEqualTo(PAYMENT_KEY);
			assertThat(result.orderId()).isEqualTo(ORDER_NUMBER);
			assertThat(result.totalAmount()).isEqualByComparingTo("75600");
			assertThat(result.approvedAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 10, 1, 12));
		}

		@Test
		@DisplayName("이미 처리된 승인 요청인데 조회한 결제 키가 다르면 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenAlreadyProcessedLookupKeyMismatches() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withBadRequest().body(ERROR_RESPONSE_ALREADY_PROCESSED)
							.contentType(MediaType.APPLICATION_JSON));
			server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + ORDER_NUMBER))
					.andRespond(withSuccess(LOOKUP_RESPONSE_DONE_OTHER_PAYMENT_KEY, MediaType.APPLICATION_JSON));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER, new BigDecimal("75600")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}

		@Test
		@DisplayName("이미 처리된 승인 요청인데 조회에서 결제를 찾지 못하면 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenAlreadyProcessedLookupNotFound() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withBadRequest().body(ERROR_RESPONSE_ALREADY_PROCESSED)
							.contentType(MediaType.APPLICATION_JSON));
			server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + ORDER_NUMBER))
					.andRespond(withStatus(HttpStatus.NOT_FOUND).body(ERROR_RESPONSE_NOT_FOUND_PAYMENT)
							.contentType(MediaType.APPLICATION_JSON));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER, new BigDecimal("75600")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}

		@Test
		@DisplayName("이미 처리된 승인 요청인데 조회한 결제에 금액이 없으면 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenAlreadyProcessedLookupTotalAmountMissing() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
					.andRespond(withBadRequest().body(ERROR_RESPONSE_ALREADY_PROCESSED)
							.contentType(MediaType.APPLICATION_JSON));
			server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + ORDER_NUMBER))
					.andRespond(withSuccess(LOOKUP_RESPONSE_DONE_WITHOUT_TOTAL_AMOUNT, MediaType.APPLICATION_JSON));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, ORDER_NUMBER, new BigDecimal("75600")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("취소하면 paymentKey 경로와 멱등키 헤더로 요청하고 마지막 취소 시각을 매핑한다")
		void sendsCancelReasonWithIdempotencyKeyAndMapsLastCanceledAt() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY + "/cancel"))
					.andExpect(method(HttpMethod.POST))
					.andExpect(header(HttpHeaders.AUTHORIZATION, EXPECTED_AUTHORIZATION))
					.andExpect(header("Idempotency-Key", "cancel-" + PAYMENT_KEY))
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
		@DisplayName("취소 이력이 없으면 취소 시각은 null 로 매핑한다")
		void mapsNullCanceledAtWhenNoCancelHistory() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY + "/cancel"))
					.andRespond(withSuccess(CANCEL_RESPONSE_WITHOUT_CANCELS, MediaType.APPLICATION_JSON));

			// when
			PaymentCancelResult result = tossPaymentClient.cancel(PAYMENT_KEY, "고객 변심");

			// then
			assertThat(result.canceledAt()).isNull();
		}

		@Test
		@DisplayName("토스가 명확히 거절하면 PAYMENT_CANCEL_FAILED 로 바꾼다")
		void throwsCancelFailedWhenTossRejects() {
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

		@Test
		@DisplayName("이미 처리된 취소 요청이면 조회로 흡수하지 않고 거절과 동일하게 PAYMENT_CANCEL_FAILED 로 바꾼다")
		void throwsCancelFailedWhenAlreadyProcessed() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY + "/cancel"))
					.andRespond(withBadRequest().body(ERROR_RESPONSE_ALREADY_PROCESSED)
							.contentType(MediaType.APPLICATION_JSON));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.cancel(PAYMENT_KEY, "고객 변심"))
					.isInstanceOf(BusinessException.class)
					.hasMessageContaining("ALREADY_PROCESSED_PAYMENT")
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_CANCEL_FAILED);
		}

		@Test
		@DisplayName("이미 취소된 결제 응답이면 취소 성공으로 흡수한다")
		void absorbsAlreadyCanceledPaymentAsSuccess() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY + "/cancel"))
					.andRespond(withBadRequest().body(tossError("ALREADY_CANCELED_PAYMENT", "이미 취소된 결제입니다."))
							.contentType(MediaType.APPLICATION_JSON));

			// when
			PaymentCancelResult result = tossPaymentClient.cancel(PAYMENT_KEY, "고객 변심");

			// then
			assertThat(result.paymentKey()).isEqualTo(PAYMENT_KEY);
			assertThat(result.status()).isEqualTo("CANCELED");
			assertThat(result.canceledAt()).isNull();
		}

		@Test
		@DisplayName("5xx 응답이면 토스가 처리했는지 알 수 없어 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenServerError() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY + "/cancel"))
					.andRespond(withServerError().body(tossError("FAILED_INTERNAL_SYSTEM_PROCESSING", "일시 오류"))
							.contentType(MediaType.APPLICATION_JSON));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.cancel(PAYMENT_KEY, "고객 변심"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}

		@Test
		@DisplayName("읽기 타임아웃이면 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenReadTimesOut() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY + "/cancel"))
					.andRespond(withException(new SocketTimeoutException("Read timed out")));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.cancel(PAYMENT_KEY, "고객 변심"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}
	}

	@Nested
	@DisplayName("lookup()")
	class Lookup {

		@Test
		@DisplayName("GET 으로 조회하면 Authorization 헤더만 싣고 상태와 마지막 취소 시각을 매핑한다")
		void sendsGetWithoutIdempotencyKeyAndMapsResponse() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + ORDER_NUMBER))
					.andExpect(method(HttpMethod.GET))
					.andExpect(header(HttpHeaders.AUTHORIZATION, EXPECTED_AUTHORIZATION))
					.andExpect(headerDoesNotExist("Idempotency-Key"))
					.andRespond(withSuccess(CANCEL_RESPONSE, MediaType.APPLICATION_JSON));

			// when
			PaymentLookupResult result = tossPaymentClient.lookup(ORDER_NUMBER);

			// then
			assertThat(result.status()).isEqualTo(PaymentLookupStatus.CANCELED);
			assertThat(result.paymentKey()).isEqualTo(PAYMENT_KEY);
			assertThat(result.method()).isEqualTo("카드");
			assertThat(result.totalAmount()).isEqualByComparingTo("75600");
			assertThat(result.approvedAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 10, 1, 12));
			assertThat(result.canceledAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 11, 32, 4));
		}

		@Test
		@DisplayName("승인 완료 결제를 조회하면 취소 시각은 null 로 매핑한다")
		void mapsNullCanceledAtForDonePayment() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + ORDER_NUMBER))
					.andRespond(withSuccess(CONFIRM_RESPONSE, MediaType.APPLICATION_JSON));

			// when
			PaymentLookupResult result = tossPaymentClient.lookup(ORDER_NUMBER);

			// then
			assertThat(result.status()).isEqualTo(PaymentLookupStatus.DONE);
			assertThat(result.canceledAt()).isNull();
		}

		@Test
		@DisplayName("토스에 결제가 없으면 NOT_FOUND 결과를 반환한다")
		void returnsNotFoundWhenTossHasNoPayment() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + ORDER_NUMBER))
					.andRespond(withStatus(HttpStatus.NOT_FOUND).body(ERROR_RESPONSE_NOT_FOUND_PAYMENT)
							.contentType(MediaType.APPLICATION_JSON));

			// when
			PaymentLookupResult result = tossPaymentClient.lookup(ORDER_NUMBER);

			// then
			assertThat(result).isEqualTo(PaymentLookupResult.notFound());
		}

		@Test
		@DisplayName("모르는 상태 문자열이면 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenStatusUnrecognized() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + ORDER_NUMBER))
					.andRespond(withSuccess(LOOKUP_RESPONSE_UNKNOWN_STATUS, MediaType.APPLICATION_JSON));

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.lookup(ORDER_NUMBER))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}

		@Test
		@DisplayName("5xx 응답이면 PAYMENT_RESULT_UNKNOWN 예외를 던진다")
		void throwsResultUnknownWhenServerError() {
			// given
			server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + ORDER_NUMBER))
					.andRespond(withServerError());

			// when & then
			assertThatThrownBy(() -> tossPaymentClient.lookup(ORDER_NUMBER))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}
	}
}
