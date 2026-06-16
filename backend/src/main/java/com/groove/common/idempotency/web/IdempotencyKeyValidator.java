package com.groove.common.idempotency.web;

import com.groove.common.idempotency.exception.IdempotencyKeyRequiredException;

import java.util.regex.Pattern;

/** Idempotency-Key 헤더 값 검증 유틸. 규칙: 공백·제어문자 없는 출력 가능 ASCII 1~255자. */
public final class IdempotencyKeyValidator {

    /** DB idempotency_record.idempotency_key 컬럼 길이와 일치. */
    public static final int MAX_LENGTH = 255;

    /** 출력 가능 ASCII(0x21~0x7E) — 공백·제어문자 불가. */
    private static final Pattern ALLOWED = Pattern.compile("^[\\x21-\\x7E]{1," + MAX_LENGTH + "}$");

    private IdempotencyKeyValidator() {
    }

    /** 헤더 값을 검증해 그대로 돌려준다. 누락·공백·과길이·허용 외 문자면 IdempotencyKeyRequiredException(HTTP 400). */
    public static String validate(String rawHeaderValue) {
        if (rawHeaderValue == null || rawHeaderValue.isBlank()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key 헤더가 필요합니다");
        }
        if (rawHeaderValue.length() > MAX_LENGTH) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key 는 " + MAX_LENGTH + "자를 넘을 수 없습니다");
        }
        if (!ALLOWED.matcher(rawHeaderValue).matches()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key 에 공백·제어문자는 쓸 수 없습니다");
        }
        return rawHeaderValue;
    }
}
