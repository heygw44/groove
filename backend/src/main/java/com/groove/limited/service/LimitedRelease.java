package com.groove.limited.service;

/** DB 트랜잭션 커밋 뒤 Redis 선점을 풀기 위해 스케줄러에 넘기는 정보. */
public record LimitedRelease(Long dropId, Long memberId) {
}
