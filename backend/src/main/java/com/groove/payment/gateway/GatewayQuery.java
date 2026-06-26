package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;

/**
 * PG 상태 재조회 결과 (#320). 권위 상태 + (보고됐다면) 권위 정산금액.
 * settledAmount 는 웹훅·폴링이 PAID 정산 전 저장 PENDING 금액과 대조해 위변조를 차단하는 데 쓴다. 미보고면 null 이고 호출부는 검증을 생략한다(Mock 은 거래별 금액 미보유 → null).
 */
public record GatewayQuery(PaymentStatus status, Long settledAmount) {

    /**
     * 정산금액이 보고됐고 저장 금액과 다르면 true — PAID 정산 전 위변조 대조(#320). 미보고(null)면 false(검증 생략).
     * settledAmount(Long)는 != 비교 시 역박싱돼 값 비교가 일어난다(null 선검사로 NPE 없음).
     */
    public boolean settledAmountMismatches(long storedAmount) {
        return settledAmount != null && settledAmount != storedAmount;
    }
}
