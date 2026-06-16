package com.groove.review.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 같은 (주문, 앨범) 조합에 이미 리뷰가 있는 경우. HTTP 409.
 */
public class DuplicateReviewException extends DomainException {

    public DuplicateReviewException() {
        super(ErrorCode.REVIEW_DUPLICATED);
    }
}
