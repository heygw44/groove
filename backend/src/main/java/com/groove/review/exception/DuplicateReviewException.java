package com.groove.review.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 같은 (주문, 앨범) 조합에 이미 리뷰가 있는 경우. HTTP 409.
 *
 * <p>1주문-1상품-1리뷰 제약 ({@code uk_review_order_album}). 서비스가 {@code existsByOrderIdAndAlbumId} 로
 * 선검증하지만, 동시 작성 경합 시 {@code saveAndFlush} 에서 {@link org.springframework.dao.DataIntegrityViolationException}
 * 으로 잡혀 이 예외로 변환된다.
 */
public class DuplicateReviewException extends DomainException {

    public DuplicateReviewException() {
        super(ErrorCode.REVIEW_DUPLICATED);
    }
}
