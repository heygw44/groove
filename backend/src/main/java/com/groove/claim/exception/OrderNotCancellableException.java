package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.order.domain.OrderStatus;

/**
 * 부분 취소를 할 수 없는 주문 상태에 취소를 요청한 경우. HTTP 422 (#238).
 *
 * <p>부분 취소(CANCEL 클레임)는 발송 전({@code PAID}/{@code PREPARING}) 주문에만 허용된다 — 배송완료({@code DELIVERED}/
 * {@code COMPLETED}) 이후의 환불은 반품(역물류, #239) 경로가 담당하며 상호 배타적이다. {@code PENDING}(미결제)은 환불할
 * 결제가 없어 취소 클레임 대상이 아니다.
 */
public class OrderNotCancellableException extends DomainException {

    public OrderNotCancellableException(OrderStatus current) {
        super(ErrorCode.CLAIM_ORDER_NOT_CANCELLABLE, "부분 취소할 수 없는 주문 상태입니다: " + current);
    }
}
