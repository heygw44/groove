package com.groove.order.domain;

/**
 * orderNumber 형식 (API.md §3.5) — {@code ORD-YYYYMMDD-XXXXXX}, suffix 는 {@code [A-Z0-9]^6}.
 *
 * <p>도메인 지식이라 발급기({@code RandomOrderNumberGenerator})와 입력 검증(컨트롤러 path / 결제 DTO)이
 * 같은 상수를 참조해 한 곳에서 형식 일관성을 유지한다. {@code @Pattern(regexp = ...)} 에 쓸 수 있도록
 * {@code public static final String} 컴파일 타임 상수.
 */
public final class OrderNumberFormat {

    /** 발급/검증 공통 정규식. */
    public static final String PATTERN = "^ORD-\\d{8}-[A-Z0-9]{6}$";

    private OrderNumberFormat() {
    }
}
