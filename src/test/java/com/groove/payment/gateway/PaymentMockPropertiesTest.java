package com.groove.payment.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentMockProperties — 바인딩 검증")
class PaymentMockPropertiesTest {

    private static PaymentMockProperties valid() {
        return new PaymentMockProperties(
                0.95,
                Duration.ofMillis(100), Duration.ofMillis(500),
                Duration.ofSeconds(1), Duration.ofSeconds(5),
                "secret");
    }

    @Test
    @DisplayName("정상 값은 그대로 통과한다")
    void validValues_pass() {
        assertThatCode(PaymentMockPropertiesTest::valid).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("min == max 인 0 지연도 허용된다 (테스트 프로파일 시나리오)")
    void zeroDelays_allowed() {
        assertThatCode(() -> new PaymentMockProperties(
                1.0, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, "s"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, 2.0, -1.0})
    @DisplayName("success-rate 가 0.0~1.0 범위를 벗어나면 실패")
    void successRate_outOfRange_throws(double rate) {
        assertThatThrownBy(() -> new PaymentMockProperties(
                rate, Duration.ofMillis(100), Duration.ofMillis(500),
                Duration.ofSeconds(1), Duration.ofSeconds(5), "s"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("success-rate");
    }

    @Test
    @DisplayName("음수 delay 는 실패")
    void negativeDelay_throws() {
        assertThatThrownBy(() -> new PaymentMockProperties(
                0.5, Duration.ofMillis(-1), Duration.ofMillis(500),
                Duration.ofSeconds(1), Duration.ofSeconds(5), "s"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delay-min");
    }

    @Test
    @DisplayName("delay-min > delay-max 는 실패")
    void delayMinGreaterThanMax_throws() {
        assertThatThrownBy(() -> new PaymentMockProperties(
                0.5, Duration.ofMillis(600), Duration.ofMillis(500),
                Duration.ofSeconds(1), Duration.ofSeconds(5), "s"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delay-min");
    }

    @Test
    @DisplayName("webhook-delay-min > webhook-delay-max 는 실패")
    void webhookDelayMinGreaterThanMax_throws() {
        assertThatThrownBy(() -> new PaymentMockProperties(
                0.5, Duration.ofMillis(100), Duration.ofMillis(500),
                Duration.ofSeconds(6), Duration.ofSeconds(5), "s"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook-delay-min");
    }

    @Test
    @DisplayName("null delay 는 실패")
    void nullDelay_throws() {
        assertThatThrownBy(() -> new PaymentMockProperties(
                0.5, null, Duration.ofMillis(500),
                Duration.ofSeconds(1), Duration.ofSeconds(5), "s"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("blank webhook-secret 은 실패")
    void blankSecret_throws() {
        assertThatThrownBy(() -> new PaymentMockProperties(
                0.5, Duration.ofMillis(100), Duration.ofMillis(500),
                Duration.ofSeconds(1), Duration.ofSeconds(5), "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook-secret");
    }

    @Test
    @DisplayName("접근자는 입력값을 그대로 반환한다")
    void accessors() {
        PaymentMockProperties p = valid();
        assertThat(p.successRate()).isEqualTo(0.95);
        assertThat(p.delayMin()).isEqualTo(Duration.ofMillis(100));
        assertThat(p.delayMax()).isEqualTo(Duration.ofMillis(500));
        assertThat(p.webhookDelayMin()).isEqualTo(Duration.ofSeconds(1));
        assertThat(p.webhookDelayMax()).isEqualTo(Duration.ofSeconds(5));
        assertThat(p.webhookSecret()).isEqualTo("secret");
    }
}
