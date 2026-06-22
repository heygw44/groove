package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RefundResponse 단위 테스트")
class RefundResponseTest {

    private static final String VALID_PG_TX = "mock-tx-abc";
    private static final Instant VALID_REFUNDED_AT = Instant.parse("2026-05-05T00:00:00Z");

    @Test
    @DisplayName("정상 값으로 생성된다")
    void valid_response_accepted() {
        RefundResponse res = new RefundResponse(VALID_PG_TX, PaymentStatus.REFUNDED, VALID_REFUNDED_AT);

        assertThat(res.pgTransactionId()).isEqualTo(VALID_PG_TX);
        assertThat(res.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(res.refundedAt()).isEqualTo(VALID_REFUNDED_AT);
    }

    @Test
    @DisplayName("pgTransactionId null 은 거부한다")
    void null_pg_tx_rejected() {
        assertThatThrownBy(() -> new RefundResponse(null, PaymentStatus.REFUNDED, VALID_REFUNDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pgTransactionId");
    }

    @Test
    @DisplayName("pgTransactionId blank 는 거부한다")
    void blank_pg_tx_rejected() {
        assertThatThrownBy(() -> new RefundResponse("  ", PaymentStatus.REFUNDED, VALID_REFUNDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pgTransactionId");
    }

    @Test
    @DisplayName("status null 은 거부한다")
    void null_status_rejected() {
        assertThatThrownBy(() -> new RefundResponse(VALID_PG_TX, null, VALID_REFUNDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("PARTIALLY_REFUNDED 도 정상 환불 상태로 허용한다")
    void partially_refunded_accepted() {
        RefundResponse res = new RefundResponse(VALID_PG_TX, PaymentStatus.PARTIALLY_REFUNDED, VALID_REFUNDED_AT);

        assertThat(res.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    @DisplayName("환불 상태가 아닌 status(PAID/PENDING/FAILED)는 거부한다")
    void non_refund_status_rejected() {
        assertThatThrownBy(() -> new RefundResponse(VALID_PG_TX, PaymentStatus.PAID, VALID_REFUNDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REFUNDED");
    }

    @Test
    @DisplayName("refundedAt null 은 거부한다")
    void null_refunded_at_rejected() {
        assertThatThrownBy(() -> new RefundResponse(VALID_PG_TX, PaymentStatus.REFUNDED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refundedAt");
    }
}
