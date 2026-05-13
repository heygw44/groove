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

        private static final String KEY = "refund:7:mock-tx-x";

        @Test
        @DisplayName("REFUNDED 상태와 환불 시각을 반환한다")
        void returnsRefunded() {
            RefundResponse response = gateway(props(1.0, Duration.ZERO, Duration.ZERO))
                    .refund(new RefundRequest("mock-tx-x", 105_000L, "고객 변심", KEY));

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

            g.refund(new RefundRequest(paid.pgTransactionId(), REQUEST.amount(), null,
                    "refund:1:" + paid.pgTransactionId()));

            assertThat(g.query(paid.pgTransactionId())).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("같은 idempotencyKey 두 번 호출 → 첫 응답을 캐시 재사용 (refundedAt 동일, PG 실호출 1회) (#72)")
        void sameKey_returnsCachedResponse() {
            MockPaymentGateway g = gateway(props(1.0, Duration.ZERO, Duration.ZERO));
            RefundRequest req = new RefundRequest("mock-tx-x", 105_000L, "변심", KEY);

            RefundResponse first = g.refund(req);
            RefundResponse second = g.refund(req);

            assertThat(second).isSameAs(first);
            assertThat(second.refundedAt()).isEqualTo(first.refundedAt());
            assertThat(g.refundCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 idempotencyKey + 다른 시각(advance) 이어도 첫 응답이 그대로 반환된다 (#72)")
        void sameKey_acrossTimeAdvance_isStable() {
            java.util.concurrent.atomic.AtomicReference<Instant> ref = new java.util.concurrent.atomic.AtomicReference<>(NOW);
            Clock advancing = new Clock() {
                @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(java.time.ZoneId zone) { return this; }
                @Override public Instant instant() { return ref.get(); }
            };
            MockPaymentGateway g = new MockPaymentGateway(
                    props(1.0, Duration.ZERO, Duration.ZERO), webhookSimulator, advancing, true);
            RefundRequest req = new RefundRequest("mock-tx-x", 105_000L, null, KEY);

            RefundResponse first = g.refund(req);
            ref.set(NOW.plusSeconds(60));
            RefundResponse second = g.refund(req);

            assertThat(second.refundedAt()).isEqualTo(first.refundedAt()); // 캐시 응답 — 시간 흘러도 불변
        }

        @Test
        @DisplayName("다른 idempotencyKey → 각자 PG 실호출 (캐시 미스)")
        void differentKeys_separateCalls() {
            MockPaymentGateway g = gateway(props(1.0, Duration.ZERO, Duration.ZERO));

            g.refund(new RefundRequest("mock-tx-x", 105_000L, null, "refund:1:mock-tx-x"));
            g.refund(new RefundRequest("mock-tx-y", 200_000L, null, "refund:2:mock-tx-y"));

            assertThat(g.refundCallCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 키 재호출은 transactions 의 REFUNDED 전이를 두 번 일으키지 않는다 (멱등)")
        void sameKey_doesNotReapplyTransition() {
            MockPaymentGateway g = gateway(props(1.0, Duration.ZERO, Duration.ZERO));
            PaymentResponse paid = g.request(REQUEST);
            String key = "refund:1:" + paid.pgTransactionId();
            RefundRequest req = new RefundRequest(paid.pgTransactionId(), REQUEST.amount(), null, key);

            g.refund(req);
            g.refund(req);

            assertThat(g.query(paid.pgTransactionId())).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(g.refundCallCount()).isEqualTo(1);
        }
    }
}
