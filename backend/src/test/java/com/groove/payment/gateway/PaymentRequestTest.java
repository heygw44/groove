package com.groove.payment.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentRequest 단위 테스트")
class PaymentRequestTest {

    private static final String VALID_ORDER_NUMBER = "ORD-20260505-A1B2C3";
    private static final long VALID_AMOUNT = 105_000L;

    @Test
    @DisplayName("정상 값으로 생성된다")
    void valid_request_accepted() {
        PaymentRequest req = new PaymentRequest(VALID_ORDER_NUMBER, VALID_AMOUNT);

        assertThat(req.orderNumber()).isEqualTo(VALID_ORDER_NUMBER);
        assertThat(req.amount()).isEqualTo(VALID_AMOUNT);
    }

    @Test
    @DisplayName("orderNumber null 은 거부한다")
    void null_order_number_rejected() {
        assertThatThrownBy(() -> new PaymentRequest(null, VALID_AMOUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderNumber");
    }

    @Test
    @DisplayName("orderNumber blank 는 거부한다")
    void blank_order_number_rejected() {
        assertThatThrownBy(() -> new PaymentRequest("  ", VALID_AMOUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderNumber");
    }

    @Test
    @DisplayName("amount 0 은 거부한다")
    void zero_amount_rejected() {
        assertThatThrownBy(() -> new PaymentRequest(VALID_ORDER_NUMBER, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("amount 음수는 거부한다")
    void negative_amount_rejected() {
        assertThatThrownBy(() -> new PaymentRequest(VALID_ORDER_NUMBER, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }
}
