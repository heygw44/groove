package com.groove.payment.gateway.toss;

import com.groove.payment.domain.PaymentStatus;

/**
 * 토스 결제 상태 문자열을 도메인 {@link PaymentStatus} 로 매핑한다.
 * DONE→PAID, CANCELED→REFUNDED, PARTIAL_CANCELED→PARTIALLY_REFUNDED, ABORTED/EXPIRED→FAILED,
 * READY/IN_PROGRESS/WAITING_FOR_DEPOSIT→PENDING.
 * null/미지값은 IllegalStateException 으로 거부한다(영구 PENDING 흡수 방지). query/refund 경로가 502 로 정규화한다.
 */
final class TossStatusMapper {

    private TossStatusMapper() {
    }

    static PaymentStatus toPaymentStatus(String tossStatus) {
        return switch (tossStatus) {
            case "DONE" -> PaymentStatus.PAID;
            case "CANCELED" -> PaymentStatus.REFUNDED;
            case "PARTIAL_CANCELED" -> PaymentStatus.PARTIALLY_REFUNDED;
            case "ABORTED", "EXPIRED" -> PaymentStatus.FAILED;
            case "READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT" -> PaymentStatus.PENDING;
            case null -> throw new IllegalStateException("토스 status 가 null 입니다");
            default -> throw new IllegalStateException("알 수 없는 토스 status: " + tossStatus);
        };
    }
}
