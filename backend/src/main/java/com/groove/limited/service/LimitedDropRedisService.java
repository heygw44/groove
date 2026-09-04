package com.groove.limited.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 한정반 Redis 카운터. 오픈 시 재고 키를 SET NX 로 세팅하고 마감 시 stock/buyers 키를 지운다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LimitedDropRedisService {

	public static final String STOCK_KEY_PREFIX = "limited:stock:";
	public static final String BUYERS_KEY_PREFIX = "limited:buyers:";

	private final StringRedisTemplate redisTemplate;

	/** 이미 키가 있으면 덮어쓰지 않는다(SET NX). 세팅됐으면 true. */
	public boolean initStock(Long dropId, int quantity) {
		Boolean result = redisTemplate.opsForValue().setIfAbsent(stockKey(dropId), String.valueOf(quantity));
		return Boolean.TRUE.equals(result);
	}

	public void clear(Long dropId) {
		redisTemplate.delete(List.of(stockKey(dropId), buyersKey(dropId)));
	}

	/** Redis 장애·값 파싱 실패는 예외를 삼키고 empty 를 반환한다. 호출부가 DB 값으로 폴백한다. */
	public Optional<Integer> getStock(Long dropId) {
		try {
			String value = redisTemplate.opsForValue().get(stockKey(dropId));
			return value == null ? Optional.empty() : Optional.of(Integer.valueOf(value));
		} catch (DataAccessException | NumberFormatException e) {
			log.warn("한정반 재고 Redis 조회 실패, DB 폴백 dropId={}", dropId, e);
			return Optional.empty();
		}
	}

	/** 목록 조회용. multiGet 한 번으로 여러 드롭의 재고를 읽는다. */
	public Map<Long, Integer> getStocks(Collection<Long> dropIds) {
		if (dropIds.isEmpty()) {
			return Map.of();
		}
		List<Long> ids = List.copyOf(dropIds);
		try {
			List<String> keys = ids.stream().map(LimitedDropRedisService::stockKey).toList();
			List<String> values = redisTemplate.opsForValue().multiGet(keys);
			Map<Long, Integer> result = new HashMap<>();
			if (values == null) {
				return result;
			}
			for (int i = 0; i < ids.size(); i++) {
				String value = values.get(i);
				if (value != null) {
					result.put(ids.get(i), Integer.valueOf(value));
				}
			}
			return result;
		} catch (DataAccessException | NumberFormatException e) {
			log.warn("한정반 재고 Redis 일괄 조회 실패, DB 폴백 dropIds={}", ids, e);
			return Map.of();
		}
	}

	public static String stockKey(Long dropId) {
		return STOCK_KEY_PREFIX + dropId;
	}

	public static String buyersKey(Long dropId) {
		return BUYERS_KEY_PREFIX + dropId;
	}
}
