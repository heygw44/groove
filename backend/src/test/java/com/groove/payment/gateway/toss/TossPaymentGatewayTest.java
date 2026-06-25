package com.groove.payment.gateway.toss;

import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentGatewayException;
import com.groove.payment.gateway.ConfirmResponse;
import com.groove.payment.gateway.GatewayRefunds;
import com.groove.payment.gateway.PaymentRequest;
import com.groove.payment.gateway.RefundRequest;
import com.groove.payment.gateway.RefundResponse;
import com.groove.payment.gateway.TossPaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("TossPaymentGateway — confirm/query/cancel 어댑터")
class TossPaymentGatewayTest {

    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final Instant NOW = Instant.parse("2026-05-12T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private MockRestServiceServer server;
    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        // TossPaymentConfig 로 빌드한 클라이언트(baseUrl·Basic Auth 인터셉터 포함)에 MockRestServiceServer 를 끼운다.
        RestClient.Builder builder = new TossPaymentConfig().tossRestClient(validProps()).mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPaymentGateway(builder.build(), CLOCK);
    }

    private static TossPaymentProperties validProps() {
        return new TossPaymentProperties(
                BASE_URL,
                "test_ck_abc", "test_sk_abc",
                "http://localhost:8080/success", "http://localhost:8080/fail",
                Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    private static String paymentJson(String paymentKey, String status) {
        return paymentJson(paymentKey, status, "카드");
    }

    private static String paymentJson(String paymentKey, String status, String method) {
        return """
                {"paymentKey":"%s","orderId":"ORD-1","status":"%s","method":"%s","totalAmount":105000,"balanceAmount":0}
                """.formatted(paymentKey, status, method);
    }

    private static String errorJson(String code, String message) {
        return """
                {"code":"%s","message":"%s"}
                """.formatted(code, message);
    }

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("DONE 응답을 PAID 로 매핑하고 paymentKey 를 Idempotency-Key 헤더로·바디에 paymentKey/orderId/amount 를 담는다")
        void confirm_done_mapsToPaid() {
            server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Idempotency-Key", "pk_1"))
                    .andExpect(jsonPath("$.paymentKey").value("pk_1"))
                    .andExpect(jsonPath("$.orderId").value("ORD-1"))
                    .andExpect(jsonPath("$.amount").value(105_000))
                    .andRespond(withSuccess(paymentJson("pk_1", "DONE"), MediaType.APPLICATION_JSON));

            ConfirmResponse response = gateway.confirm("pk_1", "ORD-1", 105_000L);

            assertThat(response.pgTransactionId()).isEqualTo("pk_1");
            assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
            assertThat(response.method()).isEqualTo(PaymentMethod.CARD);
            server.verify();
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "카드,CARD",
                "계좌이체,BANK_TRANSFER",
                "가상계좌,VIRTUAL_ACCOUNT",
                "간편결제,EASY_PAY",
                "휴대폰,MOBILE_PHONE",
                "문화상품권,GIFT_CERTIFICATE",
        })
        @DisplayName("응답 method(한글)를 도메인 PaymentMethod 로 매핑한다 (#307 결제수단 정합성)")
        void confirm_mapsMethod(String tossMethod, PaymentMethod expected) {
            server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                    .andRespond(withSuccess(paymentJson("pk_1", "DONE", tossMethod), MediaType.APPLICATION_JSON));

            ConfirmResponse response = gateway.confirm("pk_1", "ORD-1", 105_000L);

            assertThat(response.method()).isEqualTo(expected);
            server.verify();
        }

        @Test
        @DisplayName("알 수 없는 method 는 보정 스킵용 null 로 두되 결제는 성공시킨다 (status 미지와 달리 예외 없음)")
        void confirm_unknownMethod_nullButPaid() {
            server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                    .andRespond(withSuccess(paymentJson("pk_1", "DONE", "이상한수단"), MediaType.APPLICATION_JSON));

            ConfirmResponse response = gateway.confirm("pk_1", "ORD-1", 105_000L);

            assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
            assertThat(response.method()).isNull();
            server.verify();
        }

        @Test
        @DisplayName("토스 4xx 에러는 어댑터가 PaymentGatewayException(502) 으로 래핑한다")
        void confirm_clientError_wrappedToGatewayException() {
            server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .body(errorJson("INVALID_REQUEST", "잘못된 요청"))
                            .contentType(MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> gateway.confirm("pk_1", "ORD-1", 105_000L))
                    .isInstanceOf(PaymentGatewayException.class);
            server.verify();
        }
    }

    @Nested
    @DisplayName("query()")
    class Query {

        @ParameterizedTest
        @ValueSource(strings = {"READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT"})
        @DisplayName("진행중 상태는 PENDING 으로 매핑한다 (폴링 스케줄러가 재시도)")
        void query_inProgress_returnsPending(String tossStatus) {
            server.expect(requestTo(BASE_URL + "/v1/payments/pk_1"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(paymentJson("pk_1", tossStatus), MediaType.APPLICATION_JSON));

            assertThat(gateway.query("pk_1")).isEqualTo(PaymentStatus.PENDING);
            server.verify();
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"DONE,PAID", "ABORTED,FAILED", "EXPIRED,FAILED"})
        @DisplayName("종착 상태를 도메인 상태로 매핑한다")
        void query_terminal_maps(String tossStatus, PaymentStatus expected) {
            server.expect(requestTo(BASE_URL + "/v1/payments/pk_1"))
                    .andRespond(withSuccess(paymentJson("pk_1", tossStatus), MediaType.APPLICATION_JSON));

            assertThat(gateway.query("pk_1")).isEqualTo(expected);
            server.verify();
        }

        @Test
        @DisplayName("토스 5xx 에러는 어댑터가 PaymentGatewayException(502) 으로 래핑한다")
        void query_serverError_wrappedToGatewayException() {
            server.expect(requestTo(BASE_URL + "/v1/payments/pk_1"))
                    .andRespond(withServerError());

            assertThatThrownBy(() -> gateway.query("pk_1"))
                    .isInstanceOf(PaymentGatewayException.class);
            server.verify();
        }

        @Test
        @DisplayName("알 수 없는 토스 상태(200)도 PaymentGatewayException(502) 으로 래핑한다")
        void query_unknownStatus_wrappedToGatewayException() {
            server.expect(requestTo(BASE_URL + "/v1/payments/pk_1"))
                    .andRespond(withSuccess(paymentJson("pk_1", "SOMETHING_NEW"), MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> gateway.query("pk_1"))
                    .isInstanceOf(PaymentGatewayException.class);
            server.verify();
        }
    }

    @Nested
    @DisplayName("refund() — cancel")
    class Refund {

        @Test
        @DisplayName("전액 취소: Idempotency-Key 헤더·cancelReason·cancelAmount 를 보내고 CANCELED→REFUNDED 로 매핑한다")
        void refund_full_mapsToRefunded() {
            server.expect(requestTo(BASE_URL + "/v1/payments/pk_1/cancel"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Idempotency-Key", "refund:1:pk_1"))
                    .andExpect(jsonPath("$.cancelReason").value("고객 변심"))
                    .andExpect(jsonPath("$.cancelAmount").value(105_000))
                    .andRespond(withSuccess(paymentJson("pk_1", "CANCELED"), MediaType.APPLICATION_JSON));

            RefundResponse response = gateway.refund(
                    new RefundRequest("pk_1", 105_000L, "고객 변심", "refund:1:pk_1"));

            assertThat(response.pgTransactionId()).isEqualTo("pk_1");
            assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(response.refundedAt()).isEqualTo(NOW);
            server.verify();
        }

        @Test
        @DisplayName("reason 이 null 이면 기본 cancelReason 을 채워 보낸다 (토스 cancelReason 필수)")
        void refund_nullReason_sendsDefaultCancelReason() {
            server.expect(requestTo(BASE_URL + "/v1/payments/pk_1/cancel"))
                    .andExpect(jsonPath("$.cancelReason").value(TossPaymentGateway.DEFAULT_CANCEL_REASON))
                    .andRespond(withSuccess(paymentJson("pk_1", "CANCELED"), MediaType.APPLICATION_JSON));

            RefundResponse response = gateway.refund(
                    new RefundRequest("pk_1", 105_000L, null, "refund:1:pk_1"));

            assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
            server.verify();
        }

        @Test
        @DisplayName("부분 취소: PARTIAL_CANCELED→PARTIALLY_REFUNDED 로 매핑한다")
        void refund_partial_mapsToPartiallyRefunded() {
            server.expect(requestTo(BASE_URL + "/v1/payments/pk_1/cancel"))
                    .andRespond(withSuccess(paymentJson("pk_1", "PARTIAL_CANCELED"), MediaType.APPLICATION_JSON));

            RefundResponse response = gateway.refund(
                    new RefundRequest("pk_1", 50_000L, "부분 반품", "refund:1:claim:9"));

            assertThat(response.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
            server.verify();
        }

        @Test
        @DisplayName("어댑터는 토스 오류를 래핑하지 않고 raw RestClientResponseException 을 전파한다 (더블래핑 방지)")
        void refund_error_propagatesRawException() {
            server.expect(requestTo(BASE_URL + "/v1/payments/pk_1/cancel"))
                    .andRespond(withServerError());

            assertThatThrownBy(() -> gateway.refund(new RefundRequest("pk_1", 1_000L, "x", "refund:1:pk_1")))
                    .isInstanceOf(RestClientResponseException.class)
                    .isNotInstanceOf(PaymentGatewayException.class);
            server.verify();
        }

        @Test
        @DisplayName("GatewayRefunds 로 감싸면 PaymentGatewayException(502) 으로 정규화된다")
        void refund_wrappedByGatewayRefunds_becomesGatewayException() {
            server.expect(requestTo(BASE_URL + "/v1/payments/pk_1/cancel"))
                    .andRespond(withServerError());

            assertThatThrownBy(() -> GatewayRefunds.refund(
                    gateway, new RefundRequest("pk_1", 1_000L, "x", "refund:1:pk_1")))
                    .isInstanceOf(PaymentGatewayException.class);
            server.verify();
        }
    }

    @Test
    @DisplayName("request() 는 토스 confirm 모델이라 미지원이다")
    void request_unsupported() {
        assertThatThrownBy(() -> gateway.request(new PaymentRequest("ORD-1", 1_000L)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
