package com.groove.payment.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TossPaymentProperties — 바인딩 검증")
class TossPaymentPropertiesTest {

    private static TossPaymentProperties valid() {
        return new TossPaymentProperties(
                "https://api.tosspayments.com",
                "test_ck_abc", "test_sk_abc",
                "http://localhost:8080/success", "http://localhost:8080/fail",
                Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("정상 값은 그대로 통과한다")
    void validValues_pass() {
        assertThatCode(TossPaymentPropertiesTest::valid).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("base-url 이 null/blank 면 토스 기본 URL 로 폴백한다")
    void blankBaseUrl_fallsBackToDefault() {
        TossPaymentProperties nullBase = new TossPaymentProperties(
                null, "test_ck_abc", "test_sk_abc", "http://s", "http://f", null, null);
        TossPaymentProperties blankBase = new TossPaymentProperties(
                "   ", "test_ck_abc", "test_sk_abc", "http://s", "http://f", null, null);
        assertThat(nullBase.baseUrl()).isEqualTo("https://api.tosspayments.com");
        assertThat(blankBase.baseUrl()).isEqualTo("https://api.tosspayments.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("blank client-key 는 실패")
    void blankClientKey_throws(String clientKey) {
        assertThatThrownBy(() -> new TossPaymentProperties(
                null, clientKey, "test_sk_abc", "http://s", "http://f", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-key");
    }

    @Test
    @DisplayName("null client-key 는 실패")
    void nullClientKey_throws() {
        assertThatThrownBy(() -> new TossPaymentProperties(
                null, null, "test_sk_abc", "http://s", "http://f", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-key");
    }

    @Test
    @DisplayName("blank secret-key 는 실패")
    void blankSecretKey_throws() {
        assertThatThrownBy(() -> new TossPaymentProperties(
                null, "test_ck_abc", "  ", "http://s", "http://f", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret-key");
    }

    @Test
    @DisplayName(".env.example 의 secret-key 플레이스홀더는 거부한다 (이슈 #165)")
    void placeholderSecretKey_throws() {
        assertThatThrownBy(() -> new TossPaymentProperties(
                null, "test_ck_abc", "change-this-toss-secret-key-in-production",
                "http://s", "http://f", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(".env.example");
    }

    @Test
    @DisplayName("client-key 는 공개 키라 시크릿 가드를 적용하지 않는다 (change-this 가 들어가도 통과)")
    void clientKey_notGuarded_pass() {
        assertThatCode(() -> new TossPaymentProperties(
                null, "change-this-client-key", "test_sk_abc", "http://s", "http://f", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("blank success-url 은 실패")
    void blankSuccessUrl_throws() {
        assertThatThrownBy(() -> new TossPaymentProperties(
                null, "test_ck_abc", "test_sk_abc", "  ", "http://f", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("success-url");
    }

    @Test
    @DisplayName("blank fail-url 은 실패")
    void blankFailUrl_throws() {
        assertThatThrownBy(() -> new TossPaymentProperties(
                null, "test_ck_abc", "test_sk_abc", "http://s", "  ", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fail-url");
    }

    @Test
    @DisplayName("타임아웃이 null 이면 기본값(2s/5s)으로 폴백한다")
    void nullTimeouts_fallBackToDefault() {
        TossPaymentProperties p = new TossPaymentProperties(
                null, "test_ck_abc", "test_sk_abc", "http://s", "http://f", null, null);
        assertThat(p.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(p.readTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("0 또는 음수 connect-timeout 은 실패")
    void nonPositiveConnectTimeout_throws() {
        assertThatThrownBy(() -> new TossPaymentProperties(
                null, "test_ck_abc", "test_sk_abc", "http://s", "http://f", Duration.ZERO, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connect-timeout");
        assertThatThrownBy(() -> new TossPaymentProperties(
                null, "test_ck_abc", "test_sk_abc", "http://s", "http://f", Duration.ofMillis(-1), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connect-timeout");
    }

    @Test
    @DisplayName("음수 read-timeout 은 실패")
    void negativeReadTimeout_throws() {
        assertThatThrownBy(() -> new TossPaymentProperties(
                null, "test_ck_abc", "test_sk_abc", "http://s", "http://f", null, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-timeout");
    }

    @Test
    @DisplayName("앞뒤 공백은 strip 으로 정규화되어 저장된다 (env 개행/공백 방어)")
    void whitespace_isStripped() {
        TossPaymentProperties p = new TossPaymentProperties(
                "  https://api.tosspayments.com  ", "  test_ck_abc  ", "  test_sk_abc  ",
                "  http://localhost:8080/success  ", "  http://localhost:8080/fail  ", null, null);
        assertThat(p.baseUrl()).isEqualTo("https://api.tosspayments.com");
        assertThat(p.clientKey()).isEqualTo("test_ck_abc");
        assertThat(p.secretKey()).isEqualTo("test_sk_abc");
        assertThat(p.successUrl()).isEqualTo("http://localhost:8080/success");
        assertThat(p.failUrl()).isEqualTo("http://localhost:8080/fail");
    }

    @Test
    @DisplayName("공백만 있는 값은 strip 후 blank 로 거부된다")
    void whitespaceOnly_throws() {
        assertThatThrownBy(() -> new TossPaymentProperties(
                null, "test_ck_abc", "   ", "http://s", "http://f", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret-key");
    }

    @Test
    @DisplayName("접근자는 입력값을 그대로 반환한다")
    void accessors() {
        TossPaymentProperties p = valid();
        assertThat(p.baseUrl()).isEqualTo("https://api.tosspayments.com");
        assertThat(p.clientKey()).isEqualTo("test_ck_abc");
        assertThat(p.secretKey()).isEqualTo("test_sk_abc");
        assertThat(p.successUrl()).isEqualTo("http://localhost:8080/success");
        assertThat(p.failUrl()).isEqualTo("http://localhost:8080/fail");
        assertThat(p.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(p.readTimeout()).isEqualTo(Duration.ofSeconds(5));
    }
}
