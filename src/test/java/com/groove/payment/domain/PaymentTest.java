package com.groove.payment.domain;

import com.groove.order.domain.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Payment 도메인")
class PaymentTest {

    private static final String ORDER_NUMBER = "ORD-20260512-A1B2C3";

    private Order order() {
        return Order.placeForMember(ORDER_NUMBER, 1L);
    }

    @Test
    @DisplayName("initiate: PENDING 으로 생성하고 필드를 보존한다")
    void initiate_createsPending() {
        Order order = order();

        Payment payment = Payment.initiate(order, 35000L, PaymentMethod.CARD, "MOCK", "mock-tx-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getOrder()).isSameAs(order);
        assertThat(payment.getAmount()).isEqualTo(35000L);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.getPgProvider()).isEqualTo("MOCK");
        assertThat(payment.getPgTransactionId()).isEqualTo("mock-tx-1");
        assertThat(payment.getPaidAt()).isNull();
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("initiate: amount 가 0 이하면 거부한다")
    void initiate_rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Payment.initiate(order(), 0L, PaymentMethod.MOCK, "MOCK", "tx"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.initiate(order(), -1L, PaymentMethod.MOCK, "MOCK", "tx"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("initiate: pgProvider/pgTransactionId 가 blank 면 거부한다")
    void initiate_rejectsBlankPgFields() {
        assertThatThrownBy(() -> Payment.initiate(order(), 1000L, PaymentMethod.MOCK, " ", "tx"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.initiate(order(), 1000L, PaymentMethod.MOCK, "MOCK", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("initiate: order/method 가 null 이면 거부한다")
    void initiate_rejectsNulls() {
        assertThatThrownBy(() -> Payment.initiate(null, 1000L, PaymentMethod.MOCK, "MOCK", "tx"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Payment.initiate(order(), 1000L, null, "MOCK", "tx"))
                .isInstanceOf(NullPointerException.class);
    }
}
