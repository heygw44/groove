package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentResponse 단위 테스트")
class PaymentResponseTest {

    private static final String VALID_PG_TX = "mock-tx-abc";
    private static final String VALID_PROVIDER = "MOCK";

    @Test
    @DisplayName("정상 값(PENDING)으로 생성된다")
    void valid_response_accepted() {
        PaymentResponse res = new PaymentResponse(VALID_PG_TX, PaymentStatus.PENDING, VALID_PROVIDER);

        assertThat(res.pgTransactionId()).isEqualTo(VALID_PG_TX);
        assertThat(res.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(res.provider()).isEqualTo(VALID_PROVIDER);
    }

    @Test
    @DisplayName("pgTransactionId null 은 거부한다")
    void null_pg_tx_rejected() {
        assertThatThrownBy(() -> new PaymentResponse(null, PaymentStatus.PENDING, VALID_PROVIDER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pgTransactionId");
    }

    @Test
    @DisplayName("pgTransactionId blank 는 거부한다")
    void blank_pg_tx_rejected() {
        assertThatThrownBy(() -> new PaymentResponse("  ", PaymentStatus.PENDING, VALID_PROVIDER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pgTransactionId");
    }

    @Test
    @DisplayName("status 가 PAID 면 거부한다 (request 응답은 항상 PENDING)")
    void paid_status_rejected() {
        assertThatThrownBy(() -> new PaymentResponse(VALID_PG_TX, PaymentStatus.PAID, VALID_PROVIDER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("status 가 FAILED 면 거부한다")
    void failed_status_rejected() {
        assertThatThrownBy(() -> new PaymentResponse(VALID_PG_TX, PaymentStatus.FAILED, VALID_PROVIDER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("status 가 null 이면 거부한다")
    void null_status_rejected() {
        assertThatThrownBy(() -> new PaymentResponse(VALID_PG_TX, null, VALID_PROVIDER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("provider null 은 거부한다")
    void null_provider_rejected() {
        assertThatThrownBy(() -> new PaymentResponse(VALID_PG_TX, PaymentStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
    }

    @Test
    @DisplayName("provider blank 는 거부한다")
    void blank_provider_rejected() {
        assertThatThrownBy(() -> new PaymentResponse(VALID_PG_TX, PaymentStatus.PENDING, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
    }
}
