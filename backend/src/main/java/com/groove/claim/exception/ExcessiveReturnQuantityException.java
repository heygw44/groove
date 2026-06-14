package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 반품 가능 수량을 초과해 반품을 요청한 경우. HTTP 409.
 *
 * <p>한 OrderItem 의 반품 가능 수량 = 주문 수량 − 이미 활성/완료된 반품(claim)의 해당 항목 수량 합 (#239).
 * 이를 넘는 수량을 요청하면 던진다.
 */
public class ExcessiveReturnQuantityException extends DomainException {

    public ExcessiveReturnQuantityException(Long orderItemId, int requested, int returnable) {
        super(ErrorCode.CLAIM_QUANTITY_EXCEEDED,
                "반품 가능 수량을 초과했습니다: orderItemId=" + orderItemId + ", 요청=" + requested + ", 가능=" + returnable);
    }
}
