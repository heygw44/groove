package com.groove.payment.domain;

/**
 * 결제 수단 (glossary §3.6, ERD §6).
 *
 * <p>v1 Mock PG 의 시연용 분기 값이다 — 실 PG 도입 시에도 동일 enum 으로 매핑한다.
 * 클라이언트가 결제 요청 본문({@code POST /payments}) 의 {@code method} 로 전달한다.
 */
public enum PaymentMethod {
    /** 신용/체크카드 (Mock 시연용 분기). */
    CARD,
    /** 계좌이체 (Mock 시연용 분기). */
    BANK_TRANSFER,
    /** 수단 미특정 (Mock PG 기본값). */
    MOCK
}
