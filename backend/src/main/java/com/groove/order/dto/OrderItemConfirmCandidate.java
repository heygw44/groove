package com.groove.order.dto;

import java.time.LocalDateTime;

/** 자동 구매확정 후보. (deliveredAt, id) 를 다음 조회의 keyset 커서로 쓴다. */
public record OrderItemConfirmCandidate(Long id, LocalDateTime deliveredAt) {
}
