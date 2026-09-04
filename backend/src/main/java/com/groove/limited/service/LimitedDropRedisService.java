package com.groove.limited.service;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** 한정반 Redis 카운터. 오픈 시 재고 키를 SET NX 로 세팅하고 마감 시 stock/buyers 키를 지운다. */
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

	public static String stockKey(Long dropId) {
		return STOCK_KEY_PREFIX + dropId;
	}

	public static String buyersKey(Long dropId) {
		return BUYERS_KEY_PREFIX + dropId;
	}
}
