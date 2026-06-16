package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 주문에 적용할 수 없는 쿠폰/조건. HTTP 422.
 *
 * <p>대표 케이스: 게스트 주문에 memberCouponId 가 동봉된 경우.
 */
public class CouponNotApplicableException extends DomainException {

    public CouponNotApplicableException(String reason) {
        super(ErrorCode.COUPON_NOT_APPLICABLE, "주문에 적용할 수 없는 쿠폰입니다: " + reason);
    }
}
