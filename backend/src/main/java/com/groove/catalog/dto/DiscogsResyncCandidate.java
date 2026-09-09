package com.groove.catalog.dto;

/** 재검증 후보 상품 한 건. */
public record DiscogsResyncCandidate(Long productId, Long discogsReleaseId) {
}
