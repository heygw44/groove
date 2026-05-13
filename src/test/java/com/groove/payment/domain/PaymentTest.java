package com.groove.payment.domain;

import com.groove.order.domain.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Payment 도메인")
class PaymentTest {

    private static final String ORDER_NUMBER = "ORD-20260512-A1B2C3";

    private Order order() {
        return Order.placeForMember(ORDER_NUMBER, 1L, com.groove.support.OrderFixtures.sampleShippingInfo());
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
    @DisplayName("initiate: pgProvider/pgTransactionId 길이 경계 — 상한은 허용, 초과는 거부")
    void initiate_validatesPgFieldLengths() {
        String maxProvider = "P".repeat(Payment.MAX_PG_PROVIDER_LENGTH);
        String maxTxId = "T".repeat(Payment.MAX_PG_TRANSACTION_ID_LENGTH);

        Payment payment = Payment.initiate(order(), 1000L, PaymentMethod.MOCK, maxProvider, maxTxId);
        assertThat(payment.getPgProvider()).isEqualTo(maxProvider);
        assertThat(payment.getPgTransactionId()).isEqualTo(maxTxId);

        assertThatThrownBy(() -> Payment.initiate(order(), 1000L, PaymentMethod.MOCK,
                "P".repeat(Payment.MAX_PG_PROVIDER_LENGTH + 1), maxTxId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.initiate(order(), 1000L, PaymentMethod.MOCK,
                maxProvider, "T".repeat(Payment.MAX_PG_TRANSACTION_ID_LENGTH + 1)))
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

    private Payment pending(String pgTransactionId) {
        return Payment.initiate(order(), 35000L, PaymentMethod.CARD, "MOCK", pgTransactionId);
    }

    @Test
    @DisplayName("markPaid: PENDING → PAID 로 전이하고 paidAt 을 기록한다")
    void markPaid_transitionsAndRecordsPaidAt() {
        Payment payment = pending("tx-paid");
        Instant before = Instant.now();

        payment.markPaid();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull().isAfterOrEqualTo(before);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("markFailed: PENDING → FAILED 로 전이하고 사유를 기록한다")
    void markFailed_transitionsAndRecordsReason() {
        Payment payment = pending("tx-fail");

        payment.markFailed("카드 한도 초과");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("카드 한도 초과");
        assertThat(payment.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("markFailed: 사유가 상한을 넘으면 잘라서 기록한다")
    void markFailed_truncatesLongReason() {
        Payment payment = pending("tx-long");

        payment.markFailed("X".repeat(Payment.MAX_FAILURE_REASON_LENGTH + 100));

        assertThat(payment.getFailureReason()).hasSize(Payment.MAX_FAILURE_REASON_LENGTH);
    }

    @Test
    @DisplayName("markFailed: 사유 null 도 허용한다 (사유 미상)")
    void markFailed_allowsNullReason() {
        Payment payment = pending("tx-null");

        payment.markFailed(null);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("종착 상태에서 markPaid/markFailed 는 IllegalStateException")
    void terminalState_furtherTransitionsRejected() {
        Payment paid = pending("tx-1");
        paid.markPaid();
        assertThatThrownBy(paid::markPaid).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> paid.markFailed("x")).isInstanceOf(IllegalStateException.class);

        Payment failed = pending("tx-2");
        failed.markFailed("x");
        assertThatThrownBy(failed::markPaid).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.markFailed("y")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markRefunded: PAID → REFUNDED 로 전이한다 (paidAt/failureReason 은 보존)")
    void markRefunded_transitionsFromPaid() {
        Payment payment = pending("tx-refund");
        payment.markPaid();
        Instant paidAt = payment.getPaidAt();

        payment.markRefunded();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getPaidAt()).isEqualTo(paidAt);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("markRefunded: PAID 가 아닌 결제(PENDING/FAILED/REFUNDED)에 호출하면 IllegalStateException")
    void markRefunded_rejectsNonPaid() {
        Payment pending = pending("tx-p");
        assertThatThrownBy(pending::markRefunded).isInstanceOf(IllegalStateException.class);

        Payment failed = pending("tx-f");
        failed.markFailed("x");
        assertThatThrownBy(failed::markRefunded).isInstanceOf(IllegalStateException.class);

        Payment refunded = pending("tx-r");
        refunded.markPaid();
        refunded.markRefunded();
        assertThatThrownBy(refunded::markRefunded).isInstanceOf(IllegalStateException.class);
    }
}
