package com.groove.review.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 리뷰를 찾을 수 없는 경우. HTTP 404.
 *
 * <p>{@code DELETE /reviews/{reviewId}} 에서 reviewId 에 해당하는 리뷰가 없을 때 던진다 — ID 는 응답에 노출하지 않는다.
 */
public class ReviewNotFoundException extends DomainException {

    public ReviewNotFoundException() {
        super(ErrorCode.REVIEW_NOT_FOUND);
    }
}
