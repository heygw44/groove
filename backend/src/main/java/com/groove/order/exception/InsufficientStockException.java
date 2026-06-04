package com.groove.order.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 재고 부족으로 주문을 생성할 수 없는 경우 (HTTP 409).
 *
 * <p>{@code album.adjustStock(-qty)} 의 음수 가드는 422 매핑이지만, 본 도메인은 issue #43 DoD 에 따라
 * 부족 시 409 를 요구한다 — 따라서 재고 차감 직전에 본 예외로 명시 분기한다.
 */
public class InsufficientStockException extends DomainException {

    public InsufficientStockException(Long albumId, int requested, int available) {
        super(ErrorCode.ORDER_INSUFFICIENT_STOCK,
                "재고가 부족합니다: albumId=" + albumId + ", requested=" + requested + ", available=" + available);
    }
}
