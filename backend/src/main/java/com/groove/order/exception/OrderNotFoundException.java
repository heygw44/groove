package com.groove.order.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 주문을 찾을 수 없는 경우. HTTP 404.
 *
 * <p>본 이슈(#42) 범위에서는 직접 던져지지 않는다 — 후속 #W6-3 주문 생성/조회 API 에서
 * Repository 결과가 비었을 때 사용한다. ErrorCode 와 동시에 도입해 후속 작업의 임포트 비용을 줄인다.
 */
public class OrderNotFoundException extends DomainException {

    public OrderNotFoundException() {
        super(ErrorCode.ORDER_NOT_FOUND);
    }
}
