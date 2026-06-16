package com.groove.review.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 리뷰를 찾을 수 없는 경우. HTTP 404.
 */
public class ReviewNotFoundException extends DomainException {

    public ReviewNotFoundException() {
        super(ErrorCode.REVIEW_NOT_FOUND);
    }
}
