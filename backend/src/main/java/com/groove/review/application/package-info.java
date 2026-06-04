/**
 * 리뷰 application 계층 — 작성/조회/삭제 트랜잭션 경계 ({@link com.groove.review.application.ReviewService}).
 *
 * <p>작성은 주문 존재 → 본인 주문 → 배송 완료(DELIVERED 이상) → 항목 포함 → 중복 없음 순으로 검증한다.
 * 조회는 공개 목록이라 작성자명을 마스킹하고, 삭제는 작성자 본인만 가능하다. 카탈로그 응답의 평점·리뷰 수는
 * {@code AlbumService} 가 {@code ReviewRepository} 의 집계 쿼리를 페이지 단위로 1회 호출해 채운다 (N+1 회피).
 */
package com.groove.review.application;
