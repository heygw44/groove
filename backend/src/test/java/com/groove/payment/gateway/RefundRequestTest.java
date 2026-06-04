package com.groove.payment.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RefundRequest 단위 테스트")
class RefundRequestTest {

    private static final String VALID_PG_TX = "mock-tx-abc";
    private static final long VALID_AMOUNT = 105_000L;
    private static final String VALID_REASON = "관리자 환불";
    private static final String VALID_KEY = "refund:42:mock-tx-abc";

    @Nested
    @DisplayName("idempotencyKey 검증 (#72)")
    class IdempotencyKey {

        @Test
        @DisplayName("정상 키로 생성된다")
        void valid_key_accepted() {
            RefundRequest req = new RefundRequest(VALID_PG_TX, VALID_AMOUNT, VALID_REASON, VALID_KEY);
            assertThat(req.idempotencyKey()).isEqualTo(VALID_KEY);
        }

        @Test
        @DisplayName("null 키는 거부한다")
        void null_key_rejected() {
            assertThatThrownBy(() -> new RefundRequest(VALID_PG_TX, VALID_AMOUNT, VALID_REASON, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("idempotencyKey");
        }

        @Test
        @DisplayName("blank 키는 거부한다")
        void blank_key_rejected() {
            assertThatThrownBy(() -> new RefundRequest(VALID_PG_TX, VALID_AMOUNT, VALID_REASON, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("idempotencyKey");
        }

        @Test
        @DisplayName("255자 초과 키는 거부한다 (Stripe 호환 한계)")
        void too_long_key_rejected() {
            String tooLong = "k".repeat(RefundRequest.MAX_IDEMPOTENCY_KEY_LENGTH + 1);
            assertThatThrownBy(() -> new RefundRequest(VALID_PG_TX, VALID_AMOUNT, VALID_REASON, tooLong))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("idempotencyKey");
        }

        @Test
        @DisplayName("정확히 255자 키는 허용한다")
        void max_length_key_accepted() {
            String maxLen = "k".repeat(RefundRequest.MAX_IDEMPOTENCY_KEY_LENGTH);
            RefundRequest req = new RefundRequest(VALID_PG_TX, VALID_AMOUNT, VALID_REASON, maxLen);
            assertThat(req.idempotencyKey()).hasSize(RefundRequest.MAX_IDEMPOTENCY_KEY_LENGTH);
        }
    }

    @Nested
    @DisplayName("기존 필드 검증 회귀")
    class ExistingFields {

        @Test
        @DisplayName("pgTransactionId blank 는 여전히 거부한다")
        void blank_pg_tx_still_rejected() {
            assertThatThrownBy(() -> new RefundRequest("", VALID_AMOUNT, VALID_REASON, VALID_KEY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pgTransactionId");
        }

        @Test
        @DisplayName("amount <= 0 은 여전히 거부한다")
        void non_positive_amount_still_rejected() {
            assertThatThrownBy(() -> new RefundRequest(VALID_PG_TX, 0L, VALID_REASON, VALID_KEY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amount");
        }
    }
}
