package com.groove.payment.domain;

import com.groove.order.domain.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
    @DisplayName("linkPgTransaction: PENDING 결제의 잠정 pgTransactionId 를 실제 paymentKey 로 교체한다")
    void linkPgTransaction_replacesOnPending() {
        Payment payment = pending(ORDER_NUMBER); // 잠정 pgTx = orderNumber

        payment.linkPgTransaction("toss-payment-key-1");

        assertThat(payment.getPgTransactionId()).isEqualTo("toss-payment-key-1");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("linkPgTransaction: blank·길이 초과 paymentKey 는 거부한다")
    void linkPgTransaction_rejectsBlankOrTooLong() {
        assertThatThrownBy(() -> pending(ORDER_NUMBER).linkPgTransaction(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending(ORDER_NUMBER)
                .linkPgTransaction("T".repeat(Payment.MAX_PG_TRANSACTION_ID_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("linkPgTransaction: PENDING 이 아닌 결제(PAID/FAILED)는 IllegalStateException")
    void linkPgTransaction_rejectsNonPending() {
        Payment paid = pending(ORDER_NUMBER);
        paid.markPaid(Instant.now());
        assertThatThrownBy(() -> paid.linkPgTransaction("pk")).isInstanceOf(IllegalStateException.class);

        Payment failed = pending(ORDER_NUMBER);
        failed.markFailed("x");
        assertThatThrownBy(() -> failed.linkPgTransaction("pk")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("correctMethod: PENDING 결제의 잠정 method 를 confirm 이 알려준 실제 수단으로 보정한다 (#307)")
    void correctMethod_replacesOnPending() {
        Payment payment = pending(ORDER_NUMBER); // 잠정 method = CARD

        payment.correctMethod(PaymentMethod.VIRTUAL_ACCOUNT);

        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.VIRTUAL_ACCOUNT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("correctMethod: null 수단은 거부한다")
    void correctMethod_rejectsNull() {
        assertThatThrownBy(() -> pending(ORDER_NUMBER).correctMethod(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("correctMethod: PENDING 이 아닌 결제(PAID/FAILED)는 IllegalStateException")
    void correctMethod_rejectsNonPending() {
        Payment paid = pending(ORDER_NUMBER);
        paid.markPaid(Instant.now());
        assertThatThrownBy(() -> paid.correctMethod(PaymentMethod.VIRTUAL_ACCOUNT))
                .isInstanceOf(IllegalStateException.class);

        Payment failed = pending(ORDER_NUMBER);
        failed.markFailed("x");
        assertThatThrownBy(() -> failed.correctMethod(PaymentMethod.VIRTUAL_ACCOUNT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markPaid: PENDING → PAID 로 전이하고 paidAt 을 기록한다")
    void markPaid_transitionsAndRecordsPaidAt() {
        Payment payment = pending("tx-paid");
        Instant before = Instant.now();

        payment.markPaid(Instant.now());

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
        paid.markPaid(Instant.now());
        assertThatThrownBy(() -> paid.markPaid(Instant.now())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> paid.markFailed("x")).isInstanceOf(IllegalStateException.class);

        Payment failed = pending("tx-2");
        failed.markFailed("x");
        assertThatThrownBy(() -> failed.markPaid(Instant.now())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.markFailed("y")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markRefunded: PAID → REFUNDED 로 전이한다 (paidAt/failureReason 은 보존)")
    void markRefunded_transitionsFromPaid() {
        Payment payment = pending("tx-refund");
        payment.markPaid(Instant.now());
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
        refunded.markPaid(Instant.now());
        refunded.markRefunded();
        assertThatThrownBy(refunded::markRefunded).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markRefunded: 전액 환불이므로 refundedAmount 를 결제액 전액으로 채운다 (#239)")
    void markRefunded_setsRefundedAmountToFull() {
        Payment payment = pending("tx-full");
        payment.markPaid(Instant.now());

        payment.markRefunded();

        assertThat(payment.getRefundedAmount()).isEqualTo(payment.getAmount());
    }

    @Test
    @DisplayName("refund: 부분 환불은 PARTIALLY_REFUNDED 로 누적, 전액 도달 시 REFUNDED (#239)")
    void refund_partialThenFull() {
        Payment payment = pending("tx-partial");
        payment.markPaid(Instant.now());
        Instant now = Instant.parse("2026-06-12T00:00:00Z");

        payment.refund(10_000L, now);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(10_000L);

        // 추가 부분 환불 — 전액 미달이라 PARTIALLY_REFUNDED 유지, 누적만.
        payment.refund(5_000L, now);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(15_000L);

        // 잔액 환불 — 누적이 전액(35000)에 도달해 REFUNDED.
        payment.refund(20_000L, now);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(35_000L);
    }

    @Test
    @DisplayName("refund: 한 번에 전액이면 곧장 REFUNDED")
    void refund_fullInOneShot() {
        Payment payment = pending("tx-oneshot");
        payment.markPaid(Instant.now());

        payment.refund(35_000L, Instant.parse("2026-06-12T00:00:00Z"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(35_000L);
    }

    @Test
    @DisplayName("refund: 누적 환불액이 결제액을 초과하면 IllegalArgumentException")
    void refund_rejectsExceedingCumulative() {
        Payment payment = pending("tx-exceed");
        payment.markPaid(Instant.now());
        Instant now = Instant.parse("2026-06-12T00:00:00Z");
        payment.refund(30_000L, now);

        assertThatThrownBy(() -> payment.refund(10_000L, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refund: amount 가 0 이하면 IllegalArgumentException")
    void refund_rejectsNonPositiveAmount() {
        Payment payment = pending("tx-zero");
        payment.markPaid(Instant.now());
        Instant now = Instant.parse("2026-06-12T00:00:00Z");

        assertThatThrownBy(() -> payment.refund(0L, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.refund(-1L, now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refund: PAID 가 아닌 결제(PENDING)에 호출하면 상태 전이 가드로 IllegalStateException")
    void refund_rejectsNonPaid() {
        Payment payment = pending("tx-notpaid");

        assertThatThrownBy(() -> payment.refund(1_000L, Instant.parse("2026-06-12T00:00:00Z")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("refundIdempotencyKey(claimId): 'refund:{id}:claim:{claimId}' 결정적 + null claimId 거부 (#239)")
    void refundIdempotencyKey_perClaim() {
        Payment payment = pending("mock-tx-claim");
        ReflectionTestUtils.setField(payment, "id", 7L);

        assertThat(payment.refundIdempotencyKey(99L)).isEqualTo("refund:7:claim:99");
        assertThat(payment.refundIdempotencyKey(99L)).isEqualTo(payment.refundIdempotencyKey(99L));
        // claim 마다 distinct 키
        assertThat(payment.refundIdempotencyKey(100L)).isNotEqualTo(payment.refundIdempotencyKey(99L));
        assertThatThrownBy(() -> payment.refundIdempotencyKey(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refundIdempotencyKey: 'refund:{id}:{pgTransactionId}' 형식을 결정적으로 반환한다 (#72)")
    void refundIdempotencyKey_returnsDeterministicValue() {
        Payment payment = pending("mock-tx-xyz");
        ReflectionTestUtils.setField(payment, "id", 42L);

        String key1 = payment.refundIdempotencyKey();
        String key2 = payment.refundIdempotencyKey();

        assertThat(key1).isEqualTo("refund:42:mock-tx-xyz");
        assertThat(key2).isEqualTo(key1);
    }

    @Test
    @DisplayName("refundIdempotencyKey: 영속화 전(id=null) 호출은 IllegalStateException — 호출 컨텍스트 방어선")
    void refundIdempotencyKey_rejectsTransient() {
        Payment transientPayment = pending("mock-tx-zzz"); // id 미주입 → null

        assertThatThrownBy(transientPayment::refundIdempotencyKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id");
    }
}
