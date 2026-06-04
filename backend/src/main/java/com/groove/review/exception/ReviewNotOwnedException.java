package com.groove.review.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 본인의 리뷰/주문이 아닌 경우. HTTP 403.
 *
 * <p>두 곳에서 던진다 — (1) {@code POST /reviews}: 대상 주문이 인증 회원의 주문이 아닐 때 (게스트 주문 포함),
 * (2) {@code DELETE /reviews/{reviewId}}: 삭제하려는 리뷰의 작성자가 인증 회원이 아닐 때.
 *
 * <p>404 가 아니라 403 으로 응답한다 — 주문번호/리뷰ID 는 추측하기 어려운 식별자라 존재 사실 노출 위험이 낮고,
 * "권한 없음"을 명확히 알리는 편이 클라이언트 디버깅에 낫다 (API.md §3.8 정책).
 */
public class ReviewNotOwnedException extends DomainException {

    public ReviewNotOwnedException() {
        super(ErrorCode.REVIEW_NOT_OWNED);
    }
}
