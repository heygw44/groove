package com.groove.order.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 재고 부족으로 주문을 생성할 수 없는 경우. HTTP 409. */
public class InsufficientStockException extends DomainException {

    public InsufficientStockException(Long albumId, int requested, int available) {
        super(ErrorCode.ORDER_INSUFFICIENT_STOCK,
                "재고가 부족합니다: albumId=" + albumId + ", requested=" + requested + ", available=" + available);
    }
}
