package com.groove.order.domain;

/** orderNumber 형식 — ORD-YYYYMMDD-XXXXXX, suffix 는 [A-Z0-9]^6. */
public final class OrderNumberFormat {

    /** 발급/검증 공통 정규식. */
    public static final String PATTERN = "^ORD-\\d{8}-[A-Z0-9]{6}$";

    private OrderNumberFormat() {
    }
}
