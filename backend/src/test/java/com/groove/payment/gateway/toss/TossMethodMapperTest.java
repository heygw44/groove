package com.groove.payment.gateway.toss;

import com.groove.payment.domain.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TossMethodMapper — 토스 결제수단(한글) → PaymentMethod 매핑")
class TossMethodMapperTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "카드,CARD",
            "계좌이체,BANK_TRANSFER",
            "가상계좌,VIRTUAL_ACCOUNT",
            "간편결제,EASY_PAY",
            "휴대폰,MOBILE_PHONE",
            "문화상품권,GIFT_CERTIFICATE",
            "도서문화상품권,GIFT_CERTIFICATE",
            "게임문화상품권,GIFT_CERTIFICATE",
    })
    @DisplayName("토스 결제수단 문자열을 도메인 수단으로 매핑한다 (상품권 3종은 GIFT_CERTIFICATE 로 통합)")
    void mapsKnownMethods(String tossMethod, PaymentMethod expected) {
        assertThat(TossMethodMapper.toPaymentMethod(tossMethod)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("null/공백 method 는 null 을 반환한다 (보정 스킵)")
    void blankMethod_returnsNull(String tossMethod) {
        assertThat(TossMethodMapper.toPaymentMethod(tossMethod)).isNull();
    }

    @Test
    @DisplayName("null method 는 null 을 반환한다 (보정 스킵)")
    void nullMethod_returnsNull() {
        assertThat(TossMethodMapper.toPaymentMethod(null)).isNull();
    }

    @Test
    @DisplayName("알 수 없는 method 는 예외 없이 null 을 반환한다 (status 미지와 달리 결제를 실패시키지 않음)")
    void unknownMethod_returnsNullWithoutThrowing() {
        assertThat(TossMethodMapper.toPaymentMethod("이상한결제수단")).isNull();
    }
}
