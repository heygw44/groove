package com.groove.common.idempotency.web;

import com.groove.common.exception.ErrorCode;
import com.groove.common.idempotency.exception.IdempotencyKeyRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IdempotencyKeyValidator — 헤더 값 검증")
class IdempotencyKeyValidatorTest {

    @Test
    @DisplayName("UUID 형태 정상 키 — 그대로 반환")
    void validKey_returned() {
        String key = "a3f1c2d4-0000-4abc-9def-0123456789ab";
        assertThat(IdempotencyKeyValidator.validate(key)).isEqualTo(key);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("null/blank — 400")
    void blank_rejected(String value) {
        assertThatThrownBy(() -> IdempotencyKeyValidator.validate(value))
                .isInstanceOf(IdempotencyKeyRequiredException.class)
                .extracting(e -> ((IdempotencyKeyRequiredException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }

    @Test
    @DisplayName("255자 초과 — 400")
    void tooLong_rejected() {
        String tooLong = "x".repeat(IdempotencyKeyValidator.MAX_LENGTH + 1);
        assertThatThrownBy(() -> IdempotencyKeyValidator.validate(tooLong))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
    }

    @Test
    @DisplayName("255자 — 통과")
    void maxLength_ok() {
        String max = "x".repeat(IdempotencyKeyValidator.MAX_LENGTH);
        assertThat(IdempotencyKeyValidator.validate(max)).hasSize(IdempotencyKeyValidator.MAX_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space", "tab\tchar", "newline\nchar", "유니코드키", "ctrlchar"})
    @DisplayName("공백/제어문자/비ASCII 포함 — 400")
    void nonPrintableAscii_rejected(String value) {
        assertThatThrownBy(() -> IdempotencyKeyValidator.validate(value))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc123", "key_with-dots.and~tilde", "UPPER-lower-0", "!@#$%^&*()"})
    @DisplayName("출력 가능 ASCII 조합 — 통과")
    void printableAscii_ok(String value) {
        assertThat(IdempotencyKeyValidator.validate(value)).isEqualTo(value);
    }
}
