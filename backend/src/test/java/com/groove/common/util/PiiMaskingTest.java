package com.groove.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiMasking 단위 테스트")
class PiiMaskingTest {

    @ParameterizedTest
    @CsvSource({
            "김,*",
            "김철,김*",
            "홍길동,홍*동",
            "김철수,김*수",
            "남궁민수,남**수"
    })
    @DisplayName("maskName — 첫·끝 보존, 가운데 마스킹")
    void maskName_masksMiddle(String input, String expected) {
        assertThat(PiiMasking.maskName(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("maskName — null/blank 는 원본 그대로")
    void maskName_blankPassThrough(String input) {
        assertThat(PiiMasking.maskName(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("maskName — 보조 평면 문자(surrogate pair)도 code point 단위로 안전 처리")
    void maskName_handlesSupplementaryCodePoints() {
        // "𠀀" 는 U+20000 (보조 평면) — 단일 code point 이나 UTF-16 으로는 2 code unit.
        // 첫·끝 글자가 온전히 보존되고 가운데만 마스킹돼야 한다(surrogate half 미노출).
        String name = "𠀀길동";
        assertThat(PiiMasking.maskName(name)).isEqualTo("𠀀*동");
        assertThat(PiiMasking.maskName("김𠀀")).isEqualTo("김*");
        assertThat(PiiMasking.maskName("𠀀")).isEqualTo("*");
    }

    @Test
    @DisplayName("maskAddress — 앞 2토큰만 남기고 이후 *** 로 마스킹")
    void maskAddress_keepsFirstTwoTokens() {
        assertThat(PiiMasking.maskAddress("서울시 강남구 테헤란로 123"))
                .isEqualTo("서울시 강남구 ***");
        assertThat(PiiMasking.maskAddress("서울특별시 강남구 테헤란로 123"))
                .isEqualTo("서울특별시 강남구 ***");
    }

    @Test
    @DisplayName("maskAddress — 2토큰은 첫 토큰만, 1토큰은 전부 가린다 (원본 그대로 반환 금지)")
    void maskAddress_shortStillMasked() {
        assertThat(PiiMasking.maskAddress("제주시 1100로")).isEqualTo("제주시 ***");
        assertThat(PiiMasking.maskAddress("서울시 강남구")).isEqualTo("서울시 ***");
        assertThat(PiiMasking.maskAddress("테헤란로123길45")).isEqualTo("***");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("maskAddress — null/empty 는 원본 그대로")
    void maskAddress_blankPassThrough(String input) {
        assertThat(PiiMasking.maskAddress(input)).isEqualTo(input);
    }
}
