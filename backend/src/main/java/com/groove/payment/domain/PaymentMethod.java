package com.groove.payment.domain;

/**
 * 결제 수단. POST /payments 요청 본문의 method 로 전달되거나, 토스 confirm 응답의 실제 수단으로 보정된다.
 *
 * <p>토스 결제위젯은 사용자가 카드 외 수단(가상계좌·간편결제 등)을 고를 수 있으므로, checkout 시 받은 잠정
 * method 는 confirm 응답({@code TossMethodMapper})으로 실제 수단에 맞춰 보정된다.
 */
public enum PaymentMethod {
    /** 신용/체크카드 (토스 "카드"). */
    CARD,
    /** 계좌이체 (토스 "계좌이체"). */
    BANK_TRANSFER,
    /** 가상계좌 (토스 "가상계좌"). */
    VIRTUAL_ACCOUNT,
    /** 간편결제 (토스 "간편결제"). */
    EASY_PAY,
    /** 휴대폰 소액결제 (토스 "휴대폰"). */
    MOBILE_PHONE,
    /** 상품권 (토스 "문화상품권"/"도서문화상품권"/"게임문화상품권" 통합). */
    GIFT_CERTIFICATE,
    /** 수단 미특정 (Mock PG 기본값). */
    MOCK
}
