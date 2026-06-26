/**
 * 리뷰 application 계층 — 작성/조회/삭제 트랜잭션 경계 (ReviewService).
 *
 * 작성은 주문 존재 → 본인 주문 → 배송 완료(DELIVERED 이상) → 항목 포함 → 중복 없음 순으로 검증한다.
 * 조회는 작성자명을 마스킹하고, 삭제는 작성자 본인만 가능하다.
 */
package com.groove.review.application;
