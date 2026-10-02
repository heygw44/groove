package com.groove.order.dto;

import java.time.LocalDateTime;

/** 자동 배송완료 후보. (shippedAt, id) 를 다음 조회의 keyset 커서로 쓴다. */
public record OrderItemDeliverCandidate(Long id, LocalDateTime shippedAt) {
}
