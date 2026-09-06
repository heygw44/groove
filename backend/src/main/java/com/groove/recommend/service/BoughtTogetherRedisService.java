package com.groove.recommend.service;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection.StringTuple;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 상품별 "함께 구매한 상품" 점수를 담는 Redis ZSET(상대 productId → 공동구매 횟수). */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoughtTogetherRedisService {

	public static final String BOUGHT_TOGETHER_KEY_PREFIX = "recommend:bought-together:";
	public static final Duration TTL = Duration.ofHours(2);

	private final StringRedisTemplate redisTemplate;

	/**
	 * 상품별 ZSET 을 통째로 교체한다. 임시 키에 채운 뒤 RENAME 으로 스왑해, 집계 도중 조회하는 쪽이 절반만 채워진
	 * ZSET 을 보지 않게 한다. 상대 상품이 없는 항목은 건너뛴다(RENAME 대상 임시 키가 생기지 않으므로).
	 */
	public void replaceAll(Map<Long, Map<Long, Long>> countsByProduct) {
		if (countsByProduct.isEmpty()) {
			return;
		}
		try {
			redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
				StringRedisConnection stringConnection = (StringRedisConnection) connection;
				long ttlSeconds = TTL.toSeconds();
				for (Map.Entry<Long, Map<Long, Long>> entry : countsByProduct.entrySet()) {
					Map<Long, Long> scoresByOtherProduct = entry.getValue();
					if (scoresByOtherProduct.isEmpty()) {
						continue;
					}
					String key = boughtTogetherKey(entry.getKey());
					String tmpKey = key + ":tmp";
					for (Map.Entry<Long, Long> score : scoresByOtherProduct.entrySet()) {
						stringConnection.zAdd(tmpKey, score.getValue(), score.getKey().toString());
					}
					stringConnection.rename(tmpKey, key);
					stringConnection.expire(key, ttlSeconds);
				}
				return null;
			});
		} catch (DataAccessException e) {
			log.warn("공동구매 집계 Redis 적재 실패", e);
		}
	}

	/** Redis 장애·값 파싱 실패는 예외를 삼키고 빈 맵을 반환한다. score 내림차순을 유지한다. */
	public Map<Long, Double> findScores(Long productId) {
		try {
			Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
					.reverseRangeWithScores(boughtTogetherKey(productId), 0, -1);
			return toScoreMap(tuples);
		} catch (DataAccessException | NumberFormatException e) {
			log.warn("공동구매 점수 Redis 조회 실패 productId={}", productId, e);
			return Map.of();
		}
	}

	/** 여러 상품의 점수를 파이프라인 한 번으로 조회한다. 결과가 없는 상품은 빈 맵으로 채운다. */
	public Map<Long, Map<Long, Double>> findScores(Collection<Long> productIds) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		try {
			List<Long> orderedIds = List.copyOf(productIds);
			List<Object> pipelinedResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
				StringRedisConnection stringConnection = (StringRedisConnection) connection;
				for (Long productId : orderedIds) {
					stringConnection.zRevRangeWithScores(boughtTogetherKey(productId), 0, -1);
				}
				return null;
			});
			Map<Long, Map<Long, Double>> scoresByProduct = new LinkedHashMap<>();
			for (int i = 0; i < orderedIds.size(); i++) {
				scoresByProduct.put(orderedIds.get(i), toScoreMap(pipelinedResults.get(i)));
			}
			return scoresByProduct;
		} catch (DataAccessException | NumberFormatException e) {
			log.warn("공동구매 점수 Redis 일괄 조회 실패 productIds={}", productIds, e);
			return Map.of();
		}
	}

	/** {@link StringTuple}(파이프라인 조회)과 {@link TypedTuple}(단건 조회) 둘 다 올 수 있어 함께 처리한다. */
	private Map<Long, Double> toScoreMap(Object tuples) {
		if (!(tuples instanceof Set<?> tupleSet) || tupleSet.isEmpty()) {
			return Map.of();
		}
		Map<Long, Double> scores = new LinkedHashMap<>();
		for (Object tuple : tupleSet) {
			if (tuple instanceof StringTuple stringTuple) {
				scores.put(Long.valueOf(stringTuple.getValueAsString()), stringTuple.getScore());
			} else if (tuple instanceof TypedTuple<?> typedTuple && typedTuple.getValue() instanceof String value) {
				scores.put(Long.valueOf(value), typedTuple.getScore());
			}
		}
		return scores;
	}

	public static String boughtTogetherKey(Long productId) {
		return BOUGHT_TOGETHER_KEY_PREFIX + productId;
	}
}
