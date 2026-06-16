package com.groove.review.application;

/**
 * 리뷰 작성 명령 — ReviewCreateRequest 와 인증 주체의 memberId 를 합쳐 서비스로 넘기는 입력 DTO.
 */
public record ReviewCreateCommand(
        Long memberId,
        String orderNumber,
        Long albumId,
        int rating,
        String content
) {
}
