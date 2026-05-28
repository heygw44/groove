package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 주문에 적용할 수 없는 쿠폰/조건 (API.md §3.9). HTTP 422.
 *
 * <p>대표 케이스: 게스트 주문에 {@code memberCouponId} 가 동봉된 경우 — 회원 전용 쿠폰을 게스트가
 * 사용하려 시도하는 경합. 다른 적용 불가 사유 (정책 매칭 실패 등) 가 v2에서 추가될 때도 같은 예외를 쓴다.
 */
public class CouponNotApplicableException extends DomainException {

    public CouponNotApplicableException(String reason) {
        super(ErrorCode.COUPON_NOT_APPLICABLE, "주문에 적용할 수 없는 쿠폰입니다: " + reason);
    }
}
