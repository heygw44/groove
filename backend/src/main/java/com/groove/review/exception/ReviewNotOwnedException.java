package com.groove.review.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 본인의 리뷰/주문이 아닌 경우. HTTP 403.
 *
 * <p>두 곳에서 던진다 — (1) POST /reviews: 대상 주문이 인증 회원의 주문이 아닐 때(게스트 주문 포함),
 * (2) DELETE /reviews/{reviewId}: 삭제하려는 리뷰의 작성자가 인증 회원이 아닐 때.
 */
public class ReviewNotOwnedException extends DomainException {

    public ReviewNotOwnedException() {
        super(ErrorCode.REVIEW_NOT_OWNED);
    }
}
