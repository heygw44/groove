package com.groove.review.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewResponse.maskName")
class ReviewResponseTest {

    @ParameterizedTest
    @CsvSource({
            "김민수, 김**",
            "이영희, 이**",
            "Lee, L**",
            "박, 박",
            "홍길, 홍*"
    })
    @DisplayName("첫 글자만 남기고 나머지는 *, 1글자는 그대로")
    void masksAllButFirstChar(String name, String expected) {
        assertThat(ReviewResponse.maskName(name)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("null/blank 는 빈 문자열")
    void blankReturnsEmpty(String name) {
        assertThat(ReviewResponse.maskName(name)).isEmpty();
    }
}
