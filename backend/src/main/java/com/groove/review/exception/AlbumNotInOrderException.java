package com.groove.review.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 리뷰 대상 albumId 가 해당 주문의 주문 항목에 포함되지 않은 경우. HTTP 422.
 */
public class AlbumNotInOrderException extends DomainException {

    public AlbumNotInOrderException() {
        super(ErrorCode.REVIEW_ALBUM_NOT_IN_ORDER);
    }
}
