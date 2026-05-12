package com.groove.review.application;

/**
 * 리뷰 작성 명령 — 컨트롤러의 {@code ReviewCreateRequest} + 인증 주체에서 추출한 {@code memberId} 를 합쳐
 * 서비스로 넘기는 입력 DTO. Bean Validation 은 컨트롤러 경계에서 끝났다고 가정한다 (도메인이 {@code rating} 만 재검증).
 *
 * @param memberId    인증된 회원 식별자 (게스트는 리뷰 불가 — null 일 수 없음)
 * @param orderNumber 리뷰 대상 주문 번호
 * @param albumId     리뷰 대상 앨범 식별자 (해당 주문에 포함돼 있어야 함)
 * @param rating      평점 1~5
 * @param content     리뷰 내용 — nullable
 */
public record ReviewCreateCommand(
        Long memberId,
        String orderNumber,
        Long albumId,
        int rating,
        String content
) {
}
