package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.order.domain.OrderStatus;

/**
 * 반품을 접수할 수 없는 주문 상태에 반품을 요청한 경우. HTTP 422.
 *
 * <p>반품은 배송완료({@code DELIVERED})/완료({@code COMPLETED}) 주문에만 허용된다 (#239) — 발송 전(PENDING/PAID/
 * PREPARING) 주문의 환불은 즉시 취소({@code AdminOrderService.refund}) 경로가 담당하며, 본 반품(역물류) 경로와
 * 상호 배타적이다.
 */
public class OrderNotReturnableException extends DomainException {

    public OrderNotReturnableException(OrderStatus current) {
        super(ErrorCode.CLAIM_ORDER_NOT_RETURNABLE, "반품할 수 없는 주문 상태입니다: " + current);
    }
}
