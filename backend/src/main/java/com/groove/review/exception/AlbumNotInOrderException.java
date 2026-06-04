package com.groove.review.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 리뷰 대상 albumId 가 해당 주문의 주문 항목에 포함되지 않은 경우. HTTP 422.
 *
 * <p>구매하지 않은 상품에 리뷰를 다는 것을 막는다 (API.md §3.8 — "해당 주문에 해당 albumId 존재").
 * 앨범 자체가 존재하지 않는 경우({@code ALBUM_NOT_FOUND}) 와 구분된다 — 여기서는 앨범은 있으나 그 주문에 없을 때다.
 */
public class AlbumNotInOrderException extends DomainException {

    public AlbumNotInOrderException() {
        super(ErrorCode.REVIEW_ALBUM_NOT_IN_ORDER);
    }
}
