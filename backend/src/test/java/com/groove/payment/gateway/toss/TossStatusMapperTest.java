package com.groove.payment.gateway.toss;

import com.groove.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TossStatusMapper — 토스 상태 → PaymentStatus 매핑")
class TossStatusMapperTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "DONE,PAID",
            "CANCELED,REFUNDED",
            "PARTIAL_CANCELED,PARTIALLY_REFUNDED",
            "ABORTED,FAILED",
            "EXPIRED,FAILED",
            "READY,PENDING",
            "IN_PROGRESS,PENDING",
            "WAITING_FOR_DEPOSIT,PENDING",
    })
    @DisplayName("토스 상태 문자열을 도메인 상태로 매핑한다")
    void mapsKnownStatuses(String tossStatus, PaymentStatus expected) {
        assertThat(TossStatusMapper.toPaymentStatus(tossStatus)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 상태는 IllegalStateException 으로 거부한다")
    void nullStatus_throws() {
        assertThatThrownBy(() -> TossStatusMapper.toPaymentStatus(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("알 수 없는 상태는 IllegalStateException 으로 거부한다")
    void unknownStatus_throws() {
        assertThatThrownBy(() -> TossStatusMapper.toPaymentStatus("SOMETHING_NEW"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SOMETHING_NEW");
    }
}
