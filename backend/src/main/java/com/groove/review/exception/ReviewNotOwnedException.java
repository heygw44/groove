package com.groove.review.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 본인의 리뷰/주문이 아닌 경우. HTTP 403.
 * POST /reviews(대상 주문이 인증 회원 주문 아님, 게스트 포함)와
 * DELETE /reviews/{reviewId}(리뷰 작성자 ≠ 인증 회원) 두 곳에서 던진다.
 */
public class ReviewNotOwnedException extends DomainException {

    public ReviewNotOwnedException() {
        super(ErrorCode.REVIEW_NOT_OWNED);
    }
}
