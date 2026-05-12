package com.groove.payment.gateway.mock;

import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.gateway.PaymentMockProperties;
import com.groove.payment.gateway.PaymentRequest;
import com.groove.payment.gateway.PaymentResponse;
import com.groove.payment.gateway.RefundRequest;
import com.groove.payment.gateway.RefundResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("MockPaymentGateway 단위 테스트")
class MockPaymentGatewayTest {

    private static final Instant NOW = Instant.parse("2026-05-12T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PaymentRequest REQUEST = new PaymentRequest("ORD-20260512-A1B2C3", 105_000L);

    @Mock
    private MockWebhookSimulator webhookSimulator;

    private static PaymentMockProperties props(double successRate, Duration processingDelay, Duration webhookDelay) {
        return new PaymentMockProperties(
                successRate, processingDelay, processingDelay, webhookDelay, webhookDelay, "secret");
    }

    private MockPaymentGateway gateway(PaymentMockProperties props) {
        return new MockPaymentGateway(props, webhookSimulator, CLOCK, true);
    }

    @Nested
    @DisplayName("request()")
    class Request {

        @Test
        @DisplayName("PENDING 응답 + mock-tx- 접두 거래 식별자 + MOCK provider 를 반환한다")
        void returnsPendingResponse() {
            PaymentResponse response = gateway(props(1.0, Duration.ZERO, Duration.ofSeconds(2))).request(REQUEST);

            assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
            assertThat(response.pgTransactionId()).startsWith("mock-tx-");
            assertThat(response.provider()).isEqualTo(MockPaymentGateway.PROVIDER);
        }

        @Test
        @DisplayName("success-rate=1.0 이면 PAID 결과로 now+webhookDelay 시각에 웹훅 콜백을 예약한다")
        void schedulesPaidCallback_whenSuccessRateOne() {
            PaymentResponse response = gateway(props(1.0, Duration.ZERO, Duration.ofSeconds(2))).request(REQUEST);

            verify(webhookSimulator).scheduleCallback(
                    eq(response.pgTransactionId()), eq(REQUEST.orderNumber()),
                    eq(PaymentStatus.PAID), eq(NOW.plusSeconds(2)));
        }

        @Test
        @DisplayName("success-rate=0.0 이면 FAILED 결과로 웹훅 콜백을 예약한다")
        void schedulesFailedCallback_whenSuccessRateZero() {
            gateway(props(0.0, Duration.ZERO, Duration.ofSeconds(1))).request(REQUEST);

            verify(webhookSimulator).scheduleCallback(
                    any(), eq(REQUEST.orderNumber()), eq(PaymentStatus.FAILED), any());
        }

        @Test
        @DisplayName("웹훅 발사 시각이 now + [min, max] 범위 안에서 선택되어 예약에 전달된다")
        void webhookFireAtWithinConfiguredRange() {
            PaymentMockProperties ranged = new PaymentMockProperties(
                    1.0, Duration.ZERO, Duration.ZERO, Duration.ofMillis(1), Duration.ofMillis(3), "secret");

            new MockPaymentGateway(ranged, webhookSimulator, CLOCK, true).request(REQUEST);

            ArgumentCaptor<Instant> fireAt = ArgumentCaptor.forClass(Instant.class);
            verify(webhookSimulator).scheduleCallback(any(), any(), any(), fireAt.capture());
            assertThat(fireAt.getValue()).isBetween(NOW.plusMillis(1), NOW.plusMillis(3));
        }

        @Test
        @DisplayName("auto-webhook 비활성이면 거래는 기록하되 웹훅 콜백을 예약하지 않는다")
        void noWebhookScheduled_whenAutoWebhookDisabled() {
            MockPaymentGateway g = new MockPaymentGateway(
                    props(1.0, Duration.ZERO, Duration.ZERO), webhookSimulator, CLOCK, false);

            PaymentResponse response = g.request(REQUEST);

            verifyNoInteractions(webhookSimulator);
            // 거래는 기록되어 폴링 조회는 가능하다.
            assertThat(g.query(response.pgTransactionId())).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        @DisplayName("0 이 아닌 처리 지연이 설정되면 그만큼 호출이 지연된다")
        void appliesProcessingLatency() {
            long start = System.nanoTime();
            gateway(props(1.0, Duration.ofMillis(20), Duration.ZERO)).request(REQUEST);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(elapsedMs).isGreaterThanOrEqualTo(15L);
        }
    }

    @Nested
    @DisplayName("query()")
    class Query {

        @Test
        @DisplayName("웹훅 발사 예정 시각 전이면 PENDING 을 반환한다")
        void pendingBeforeReadyAt() {
            MockPaymentGateway g = gateway(props(1.0, Duration.ZERO, Duration.ofSeconds(5)));
            PaymentResponse response = g.request(REQUEST);

            assertThat(g.query(response.pgTransactionId())).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("웹훅 발사 예정 시각 이후면 결정된 최종 결과를 반환한다")
        void finalResultAfterReadyAt() {
            MockPaymentGateway g = gateway(props(1.0, Duration.ZERO, Duration.ZERO));
            PaymentResponse response = g.request(REQUEST);

            assertThat(g.query(response.pgTransactionId())).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        @DisplayName("알 수 없는 거래는 PENDING 으로 응답한다")
        void unknownTransaction_returnsPending() {
            assertThat(gateway(props(1.0, Duration.ZERO, Duration.ZERO)).query("mock-tx-unknown"))
                    .isEqualTo(PaymentStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("refund()")
    class Refund {

        @Test
        @DisplayName("REFUNDED 상태와 환불 시각을 반환한다")
        void returnsRefunded() {
            RefundResponse response = gateway(props(1.0, Duration.ZERO, Duration.ZERO))
                    .refund(new RefundRequest("mock-tx-x", 105_000L, "고객 변심"));

            assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(response.pgTransactionId()).isEqualTo("mock-tx-x");
            assertThat(response.refundedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("알려진 거래를 환불하면 이후 query() 가 REFUNDED 를 반환한다")
        void knownTransaction_queryReflectsRefund() {
            MockPaymentGateway g = gateway(props(1.0, Duration.ZERO, Duration.ZERO));
            PaymentResponse paid = g.request(REQUEST);
            assertThat(g.query(paid.pgTransactionId())).isEqualTo(PaymentStatus.PAID);

            g.refund(new RefundRequest(paid.pgTransactionId(), REQUEST.amount(), null));

            assertThat(g.query(paid.pgTransactionId())).isEqualTo(PaymentStatus.REFUNDED);
        }
    }
}
