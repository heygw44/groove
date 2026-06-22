package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConfirmResponse — 동기 승인 응답 검증")
class ConfirmResponseTest {

    @Test
    @DisplayName("정상값(PAID)은 그대로 보존한다")
    void valid_paid() {
        ConfirmResponse response = new ConfirmResponse("toss-pk-1", PaymentStatus.PAID);

        assertThat(response.pgTransactionId()).isEqualTo("toss-pk-1");
        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("pgTransactionId 가 blank 이면 거부한다")
    void blankPgTransactionId_throws() {
        assertThatThrownBy(() -> new ConfirmResponse(" ", PaymentStatus.PAID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("pgTransactionId 가 null 이면 거부한다")
    void nullPgTransactionId_throws() {
        assertThatThrownBy(() -> new ConfirmResponse(null, PaymentStatus.PAID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("status 가 null 이면 거부한다")
    void nullStatus_throws() {
        assertThatThrownBy(() -> new ConfirmResponse("toss-pk-1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"REFUNDED", "PARTIALLY_REFUNDED"})
    @DisplayName("환불 상태는 confirm 결과일 수 없어 거부한다")
    void refundStatus_throws(PaymentStatus status) {
        assertThatThrownBy(() -> new ConfirmResponse("toss-pk-1", status))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
