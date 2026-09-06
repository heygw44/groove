package com.groove.recommend.service;

import java.time.LocalDateTime;

/**
 * 상품 상세 조회 이벤트.
 *
 * @param memberId 비로그인 조회면 null
 * @param productId 조회한 상품 ID
 * @param viewedAt 조회 시각
 */
public record ProductViewedEvent(Long memberId, Long productId, LocalDateTime viewedAt) {
}
