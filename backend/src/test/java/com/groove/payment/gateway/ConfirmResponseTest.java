package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentMethod;
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
    @DisplayName("정상값(PAID + 실제 결제수단)은 그대로 보존한다")
    void valid_paid() {
        ConfirmResponse response = new ConfirmResponse("toss-pk-1", PaymentStatus.PAID, PaymentMethod.VIRTUAL_ACCOUNT);

        assertThat(response.pgTransactionId()).isEqualTo("toss-pk-1");
        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.method()).isEqualTo(PaymentMethod.VIRTUAL_ACCOUNT);
    }

    @Test
    @DisplayName("method 가 null 이어도 허용한다 (Mock·미지 수단 — 호출부가 보정을 건너뜀)")
    void nullMethod_allowed() {
        ConfirmResponse response = new ConfirmResponse("toss-pk-1", PaymentStatus.PAID, null);

        assertThat(response.method()).isNull();
    }

    @Test
    @DisplayName("pgTransactionId 가 blank 이면 거부한다")
    void blankPgTransactionId_throws() {
        assertThatThrownBy(() -> new ConfirmResponse(" ", PaymentStatus.PAID, PaymentMethod.CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("pgTransactionId 가 null 이면 거부한다")
    void nullPgTransactionId_throws() {
        assertThatThrownBy(() -> new ConfirmResponse(null, PaymentStatus.PAID, PaymentMethod.CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("status 가 null 이면 거부한다")
    void nullStatus_throws() {
        assertThatThrownBy(() -> new ConfirmResponse("toss-pk-1", null, PaymentMethod.CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"REFUNDED", "PARTIALLY_REFUNDED"})
    @DisplayName("환불 상태는 confirm 결과일 수 없어 거부한다")
    void refundStatus_throws(PaymentStatus status) {
        assertThatThrownBy(() -> new ConfirmResponse("toss-pk-1", status, PaymentMethod.CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
