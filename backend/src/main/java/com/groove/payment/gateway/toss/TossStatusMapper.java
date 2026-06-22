package com.groove.payment.gateway.toss;

import com.groove.payment.domain.PaymentStatus;

/**
 * 토스 결제 상태 문자열 → 도메인 {@link PaymentStatus} 매퍼.
 *
 * <pre>
 *   DONE                                        → PAID
 *   CANCELED                                    → REFUNDED
 *   PARTIAL_CANCELED                            → PARTIALLY_REFUNDED
 *   ABORTED / EXPIRED                           → FAILED
 *   READY / IN_PROGRESS / WAITING_FOR_DEPOSIT   → PENDING (진행중 — 폴링 스케줄러가 PENDING 이면 다음 주기 재시도)
 * </pre>
 *
 * <p>null/미지값은 {@link IllegalStateException} 으로 거부한다. query 경로는 어댑터 catch 가, refund 경로는
 * GatewayRefunds 가 이를 PaymentGatewayException(502)으로 정규화한다 — 알 수 없는 상태를 영구 PENDING 으로
 * 흡수하지 않고 명시적으로 실패시킨다.
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
