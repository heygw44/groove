package com.groove.limited.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
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

	/** reserve 스크립트 반환값. */
	public enum ReserveResult {
		OK, ALREADY, SOLD_OUT, NOT_INITIALIZED
	}

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<Long> limitedReserveScript;
	private final RedisScript<Long> limitedReleaseScript;

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

	/** 재고 선점과 구매자 등록을 원자적으로 처리한다. 이미 구매했거나 재고가 없으면 아무 것도 바꾸지 않는다. */
	public ReserveResult reserve(Long dropId, Long memberId) {
		Long result = redisTemplate.execute(limitedReserveScript, List.of(stockKey(dropId), buyersKey(dropId)),
				memberId.toString());
		return toReserveResult(result);
	}

	/** 선점을 되돌린다. DB 처리 실패 등으로 뒤늦게 취소할 때 쓰이며, 실패 시 재고 불일치로 남으므로 반드시 로그를 남긴다. */
	public void release(Long dropId, Long memberId) {
		try {
			redisTemplate.execute(limitedReleaseScript, List.of(stockKey(dropId), buyersKey(dropId)),
					memberId.toString());
		} catch (DataAccessException e) {
			log.error("한정반 Redis 재고 복구 실패, 수동 복구 필요 dropId={} memberId={}", dropId, memberId, e);
		}
	}

	private static ReserveResult toReserveResult(Long result) {
		if (result == null) {
			throw new IllegalStateException("한정반 reserve 스크립트가 null 을 반환했습니다.");
		}
		return switch (result.intValue()) {
			case 0 -> ReserveResult.OK;
			case 1 -> ReserveResult.ALREADY;
			case 2 -> ReserveResult.SOLD_OUT;
			case 3 -> ReserveResult.NOT_INITIALIZED;
			default -> throw new IllegalStateException("알 수 없는 한정반 reserve 결과: " + result);
		};
	}

	public static String stockKey(Long dropId) {
		return STOCK_KEY_PREFIX + dropId;
	}

	public static String buyersKey(Long dropId) {
		return BUYERS_KEY_PREFIX + dropId;
	}
}
