package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;

/**
 * PG 상태 재조회 결과 (#320). 권위 상태와 (보고됐다면) 권위 정산금액을 함께 반환한다.
 *
 * <p>{@code settledAmount} 는 PG 가 알려준 실제 결제총액으로, 웹훅·폴링이 PAID 정산 전 저장 PENDING 금액과
 * 대조해 위변조를 차단하는 데 쓴다. PG 가 금액을 보고하지 않거나(비종착 등) 알 수 없으면 {@code null} 이며,
 * 이 경우 호출부는 금액 검증을 생략한다(예: MockPaymentGateway 는 거래별 금액을 보유하지 않아 null 을 반환).
 */
public record GatewayQuery(PaymentStatus status, Long settledAmount) {
}
