package com.groove.order.dto;

import java.time.LocalDateTime;

/** 결제 기한 만료 후보. (expiresAt, id) 를 다음 조회의 keyset 커서로 쓴다. */
public record OrderExpirationCandidate(Long id, LocalDateTime expiresAt) {
}
