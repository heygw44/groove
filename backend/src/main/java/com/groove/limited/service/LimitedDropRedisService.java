package com.groove.limited.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.groove.limited.entity.LimitedAttemptResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 한정반 Redis 카운터. 오픈 시 재고 키를 SET NX 로 세팅하고 마감 시 stock/buyers/attempts/pending/rebuild 키를 지운다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LimitedDropRedisService {

	public static final String STOCK_KEY_PREFIX = "limited:stock:";
	public static final String BUYERS_KEY_PREFIX = "limited:buyers:";
	public static final String ATTEMPTS_KEY_PREFIX = "limited:attempts:";
	public static final String PENDING_KEY_PREFIX = "limited:pending:";
	public static final String REBUILD_LOCK_KEY_PREFIX = "limited:rebuild:";

	private static final Duration REBUILD_LOCK_TTL = Duration.ofMillis(3000);

	/** reserve 스크립트 반환값. */
	public enum ReserveResult {
		OK, ALREADY, SOLD_OUT, NOT_INITIALIZED
	}

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<Long> limitedReserveScript;
	private final RedisScript<Long> limitedReleaseScript;
	private final RedisScript<List> limitedSyncScript;
	private final Clock clock;

	/** 이미 키가 있으면 덮어쓰지 않는다(SET NX). 세팅됐으면 true. */
	public boolean initStock(Long dropId, int quantity) {
		Boolean result = redisTemplate.opsForValue().setIfAbsent(stockKey(dropId), String.valueOf(quantity));
		return Boolean.TRUE.equals(result);
	}

	public void clear(Long dropId) {
		redisTemplate.delete(List.of(stockKey(dropId), buyersKey(dropId), attemptsKey(dropId), pendingKey(dropId),
				rebuildLockKey(dropId)));
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
		Long result = redisTemplate.execute(limitedReserveScript,
				List.of(stockKey(dropId), buyersKey(dropId), pendingKey(dropId)), memberId.toString(),
				String.valueOf(clock.millis()));
		return toReserveResult(result);
	}

	/** 선점을 되돌린다. 실패해도 대사 스케줄러가 DB 기준으로 복구하므로 예외를 삼키고 로그만 남긴다. */
	public void release(Long dropId, Long memberId) {
		try {
			redisTemplate.execute(limitedReleaseScript,
					List.of(stockKey(dropId), buyersKey(dropId), pendingKey(dropId)), memberId.toString());
		} catch (DataAccessException e) {
			log.error("한정반 Redis 선점 해제 실패, 대사 스케줄러가 DB 기준으로 복구한다 dropId={} memberId={}", dropId, memberId, e);
		}
	}

	/** write() 커밋 뒤 pending 표시를 지운다. 실패해도 대사가 정리하므로 예외를 삼키고 debug 로만 남긴다. */
	public void confirm(Long dropId, Long memberId) {
		try {
			redisTemplate.opsForZSet().remove(pendingKey(dropId), memberId.toString());
		} catch (DataAccessException e) {
			log.debug("한정반 Redis pending 정리 실패, 대사가 정리한다 dropId={} memberId={}", dropId, memberId, e);
		}
	}

	/**
	 * Redis stock/buyers 를 DB 기준으로 다시 계산한다. 대사·재적재·서킷 복귀가 모두 이 스크립트 하나를 쓴다.
	 * Redis 장애는 호출부(트랜잭션)가 처리하도록 예외를 그대로 던진다.
	 */
	@SuppressWarnings("unchecked")
	public LimitedSyncResult sync(Long dropId, int dbRemaining, Collection<Long> dbBuyerIds, long pendingCutoffMs) {
		List<Object> args = new ArrayList<>();
		args.add(String.valueOf(dbRemaining));
		args.add(String.valueOf(pendingCutoffMs));
		for (Long buyerId : dbBuyerIds) {
			args.add(buyerId.toString());
		}
		List<Long> result = redisTemplate.execute(limitedSyncScript,
				List.of(stockKey(dropId), buyersKey(dropId), pendingKey(dropId)), args.toArray());
		return toSyncResult(result);
	}

	/** 키 유실 재적재를 드롭당 한 요청만 하게 하는 단일 비행 락. 이미 다른 요청이 잡고 있으면 false. */
	public boolean tryLockRebuild(Long dropId) {
		Boolean result = redisTemplate.opsForValue().setIfAbsent(rebuildLockKey(dropId), "1", REBUILD_LOCK_TTL);
		return Boolean.TRUE.equals(result);
	}

	public void unlockRebuild(Long dropId) {
		redisTemplate.delete(rebuildLockKey(dropId));
	}

	/**
	 * stock 키가 없는 드롭만 골라낸다. Redis 장애를 "전부 유실"로 오판해 재적재가 폭주하지 않도록 예외를
	 * 삼키지 않고 그대로 던진다.
	 */
	public List<Long> findMissingStock(Collection<Long> dropIds) {
		if (dropIds.isEmpty()) {
			return List.of();
		}
		List<Long> orderedIds = List.copyOf(dropIds);
		List<Object> pipelinedResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
			StringRedisConnection stringConnection = (StringRedisConnection) connection;
			for (Long dropId : orderedIds) {
				stringConnection.exists(stockKey(dropId));
			}
			return null;
		});
		List<Long> missing = new ArrayList<>();
		for (int i = 0; i < orderedIds.size(); i++) {
			Boolean exists = (Boolean) pipelinedResults.get(i);
			if (!Boolean.TRUE.equals(exists)) {
				missing.add(orderedIds.get(i));
			}
		}
		return missing;
	}

	/** 실패 사유별 시도 횟수를 누적한다. 집계 실패가 구매를 막으면 안 되므로 예외를 삼키고 debug 로만 남긴다(핫패스). */
	public void recordAttempt(Long dropId, LimitedAttemptResult result) {
		try {
			HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
			hashOps.increment(attemptsKey(dropId), result.name(), 1);
		} catch (DataAccessException e) {
			log.debug("한정반 시도 집계 Redis 기록 실패 dropId={} result={}", dropId, result, e);
		}
	}

	/** 관리자 조회용. Redis 장애 시 예외를 삼키고 빈 맵으로 폴백한다. */
	public Map<LimitedAttemptResult, Long> getAttempts(Long dropId) {
		try {
			HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
			return parseAttempts(hashOps.entries(attemptsKey(dropId)));
		} catch (DataAccessException e) {
			log.warn("한정반 시도 집계 Redis 조회 실패 dropId={}", dropId, e);
			return Map.of();
		}
	}

	/**
	 * 관리자 목록 조회용. 드롭마다 HGETALL 을 따로 부르는 대신 파이프라인 한 번으로 묶는다.
	 * Redis 장애 시 예외를 삼키고 전부 빈 맵으로 폴백한다(단건 {@link #getAttempts(Long)}과 같은 의미).
	 */
	public Map<Long, Map<LimitedAttemptResult, Long>> getAttempts(Collection<Long> dropIds) {
		if (dropIds.isEmpty()) {
			return Map.of();
		}
		List<Long> orderedIds = List.copyOf(dropIds);
		try {
			List<Object> pipelinedResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
				StringRedisConnection stringConnection = (StringRedisConnection) connection;
				for (Long dropId : orderedIds) {
					stringConnection.hGetAll(attemptsKey(dropId));
				}
				return null;
			});
			Map<Long, Map<LimitedAttemptResult, Long>> attemptsByDrop = new LinkedHashMap<>();
			for (int i = 0; i < orderedIds.size(); i++) {
				@SuppressWarnings("unchecked")
				Map<String, String> entries = (Map<String, String>) pipelinedResults.get(i);
				attemptsByDrop.put(orderedIds.get(i), parseAttempts(entries == null ? Map.of() : entries));
			}
			return attemptsByDrop;
		} catch (DataAccessException e) {
			log.warn("한정반 시도 집계 Redis 일괄 조회 실패 dropIds={}", orderedIds, e);
			return Map.of();
		}
	}

	/** 마감 flush 전용. 조회 실패를 "집계 0건"과 구분해야 clear 가 데이터를 지우지 않으므로 예외를 그대로 던진다. */
	public Map<LimitedAttemptResult, Long> getAttemptsForFlush(Long dropId) {
		HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
		return parseAttempts(hashOps.entries(attemptsKey(dropId)));
	}

	private static Map<LimitedAttemptResult, Long> parseAttempts(Map<String, String> entries) {
		Map<LimitedAttemptResult, Long> result = new EnumMap<>(LimitedAttemptResult.class);
		for (Map.Entry<String, String> entry : entries.entrySet()) {
			try {
				result.put(LimitedAttemptResult.valueOf(entry.getKey()), Long.valueOf(entry.getValue()));
			} catch (IllegalArgumentException e) {
				log.warn("알 수 없는 한정반 시도 집계 필드 무시 field={} value={}", entry.getKey(), entry.getValue());
			}
		}
		return result;
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

	private static LimitedSyncResult toSyncResult(List<Long> result) {
		if (result == null || result.size() != 5) {
			throw new IllegalStateException("한정반 sync 스크립트가 알 수 없는 값을 반환했습니다: " + result);
		}
		return new LimitedSyncResult(result.get(0).intValue(), result.get(1).intValue(), result.get(2) == 1L,
				result.get(3).intValue(), result.get(4).intValue());
	}

	public static String stockKey(Long dropId) {
		return STOCK_KEY_PREFIX + dropId;
	}

	public static String buyersKey(Long dropId) {
		return BUYERS_KEY_PREFIX + dropId;
	}

	public static String attemptsKey(Long dropId) {
		return ATTEMPTS_KEY_PREFIX + dropId;
	}

	public static String pendingKey(Long dropId) {
		return PENDING_KEY_PREFIX + dropId;
	}

	public static String rebuildLockKey(Long dropId) {
		return REBUILD_LOCK_KEY_PREFIX + dropId;
	}
}
