package com.groove.review.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 배송 완료(DELIVERED 이상)되지 않은 주문에 리뷰를 작성하려 한 경우. HTTP 422.
 * 리뷰는 DELIVERED 또는 COMPLETED 상태 주문에만 허용된다.
 */
public class ReviewOrderNotDeliveredException extends DomainException {

    public ReviewOrderNotDeliveredException() {
        super(ErrorCode.REVIEW_ORDER_NOT_DELIVERED);
    }
}
