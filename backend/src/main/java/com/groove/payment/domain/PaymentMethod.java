package com.groove.payment.domain;

/**
 * 결제 수단. POST /payments 요청 본문의 method 로 전달된다.
 */
public enum PaymentMethod {
    /** 신용/체크카드. */
    CARD,
    /** 계좌이체. */
    BANK_TRANSFER,
    /** 수단 미특정 (Mock PG 기본값). */
    MOCK
}
