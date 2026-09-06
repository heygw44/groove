package com.groove.recommend.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 회원별 최근 조회 상품 Redis 리스트. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecentViewRedisService {

	public static final String RECENT_VIEW_KEY_PREFIX = "recent-view:";
	public static final int MAX_SIZE = 20;
	public static final Duration TTL = Duration.ofDays(30);

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<Long> recentViewPushScript;

	/** 목록 맨 앞에 넣고 최대 개수를 넘으면 오래된 항목을 자른다. Redis 장애는 로그만 남기고 삼킨다. */
	public void push(Long memberId, Long productId) {
		try {
			redisTemplate.execute(recentViewPushScript, List.of(recentViewKey(memberId)), productId.toString(),
					String.valueOf(MAX_SIZE), String.valueOf(TTL.toSeconds()));
		} catch (DataAccessException e) {
			log.warn("최근 조회 상품 Redis 적재 실패 memberId={} productId={}", memberId, productId, e);
		}
	}

	/** Redis 장애·값 파싱 실패는 예외를 삼키고 빈 리스트를 반환한다. */
	public List<Long> findRecentProductIds(Long memberId) {
		try {
			List<String> values = redisTemplate.opsForList().range(recentViewKey(memberId), 0, MAX_SIZE - 1);
			if (values == null) {
				return List.of();
			}
			List<Long> productIds = new ArrayList<>(values.size());
			for (String value : values) {
				productIds.add(Long.valueOf(value));
			}
			return productIds;
		} catch (DataAccessException | NumberFormatException e) {
			log.warn("최근 조회 상품 Redis 조회 실패 memberId={}", memberId, e);
			return List.of();
		}
	}

	/** 조회 시점에 이미 삭제·비공개된 상품 등을 목록에서 청소할 때 쓴다. */
	public void remove(Long memberId, List<Long> productIds) {
		try {
			String key = recentViewKey(memberId);
			for (Long productId : productIds) {
				redisTemplate.opsForList().remove(key, 0, productId.toString());
			}
		} catch (DataAccessException e) {
			log.warn("최근 조회 상품 Redis 제거 실패 memberId={} productIds={}", memberId, productIds, e);
		}
	}

	public static String recentViewKey(Long memberId) {
		return RECENT_VIEW_KEY_PREFIX + memberId;
	}
}
