package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WebhookNotification 단위 테스트")
class WebhookNotificationTest {

    private static final String VALID_PG_TX = "mock-tx-abc";
    private static final String VALID_ORDER_NUMBER = "ORD-20260505-A1B2C3";
    private static final Instant VALID_OCCURRED_AT = Instant.parse("2026-05-05T00:00:00Z");
    private static final String VALID_SIGNATURE = "mock-signature";

    @Test
    @DisplayName("정상 값(PAID)으로 생성된다")
    void valid_paid_notification_accepted() {
        WebhookNotification noti = new WebhookNotification(
                VALID_PG_TX, VALID_ORDER_NUMBER, PaymentStatus.PAID, VALID_OCCURRED_AT, VALID_SIGNATURE);

        assertThat(noti.pgTransactionId()).isEqualTo(VALID_PG_TX);
        assertThat(noti.orderNumber()).isEqualTo(VALID_ORDER_NUMBER);
        assertThat(noti.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(noti.occurredAt()).isEqualTo(VALID_OCCURRED_AT);
    }

    @Test
    @DisplayName("정상 값(FAILED)으로 생성된다")
    void valid_failed_notification_accepted() {
        WebhookNotification noti = new WebhookNotification(
                VALID_PG_TX, VALID_ORDER_NUMBER, PaymentStatus.FAILED, VALID_OCCURRED_AT, VALID_SIGNATURE);

        assertThat(noti.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("pgTransactionId blank 는 거부한다")
    void blank_pg_tx_rejected() {
        assertThatThrownBy(() -> new WebhookNotification(
                "  ", VALID_ORDER_NUMBER, PaymentStatus.PAID, VALID_OCCURRED_AT, VALID_SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pgTransactionId");
    }

    @Test
    @DisplayName("orderNumber blank 는 거부한다")
    void blank_order_number_rejected() {
        assertThatThrownBy(() -> new WebhookNotification(
                VALID_PG_TX, "  ", PaymentStatus.PAID, VALID_OCCURRED_AT, VALID_SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderNumber");
    }

    @Test
    @DisplayName("status 가 PENDING 이면 거부한다 (웹훅은 최종 결과만 통보)")
    void pending_status_rejected() {
        assertThatThrownBy(() -> new WebhookNotification(
                VALID_PG_TX, VALID_ORDER_NUMBER, PaymentStatus.PENDING, VALID_OCCURRED_AT, VALID_SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAID 또는 FAILED");
    }

    @Test
    @DisplayName("status 가 REFUNDED 면 거부한다")
    void refunded_status_rejected() {
        assertThatThrownBy(() -> new WebhookNotification(
                VALID_PG_TX, VALID_ORDER_NUMBER, PaymentStatus.REFUNDED, VALID_OCCURRED_AT, VALID_SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAID 또는 FAILED");
    }

    @Test
    @DisplayName("status 가 null 이면 거부한다")
    void null_status_rejected() {
        assertThatThrownBy(() -> new WebhookNotification(
                VALID_PG_TX, VALID_ORDER_NUMBER, null, VALID_OCCURRED_AT, VALID_SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAID 또는 FAILED");
    }

    @Test
    @DisplayName("occurredAt null 은 거부한다")
    void null_occurred_at_rejected() {
        assertThatThrownBy(() -> new WebhookNotification(
                VALID_PG_TX, VALID_ORDER_NUMBER, PaymentStatus.PAID, null, VALID_SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("occurredAt");
    }
}
