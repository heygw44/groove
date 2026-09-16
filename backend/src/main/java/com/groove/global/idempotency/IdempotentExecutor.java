package com.groove.global.idempotency;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 범용 멱등 실행기. {@code SET NX PX} 로 처리 시작을 선점하고, 완료되면 응답을 같은 키에 남겨 재요청 시
 * 재생한다. action 은 스프링 프록시를 통해 호출되는 트랜잭션 메서드라 반환 시점에 이미 커밋돼 있으므로,
 * afterCompletion 콜백 대신 여기 트랜잭션 밖 try/catch 로 키 정리를 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentExecutor {

	private static final Duration TTL = Duration.ofMinutes(10);

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public <T> IdempotentResult<T> execute(String scope, Long memberId, String key, Object request,
			Class<T> responseType, Supplier<T> action) {
		String redisKey = buildKey(scope, memberId, key);
		String requestHash = hash(request);
		return acquire(redisKey, requestHash, responseType, action, true);
	}

	private <T> IdempotentResult<T> acquire(String redisKey, String requestHash, Class<T> responseType,
			Supplier<T> action, boolean allowRetryOnMissingValue) {
		boolean acquired;
		try {
			acquired = Boolean.TRUE.equals(redisTemplate.opsForValue()
					.setIfAbsent(redisKey, serialize(StoredEntry.processing(requestHash)), TTL));
		} catch (DataAccessException e) {
			log.warn("멱등 키 저장 실패, 멱등 없이 처리한다 key={}", redisKey, e);
			return new IdempotentResult<>(action.get(), false);
		}
		if (acquired) {
			return runAction(redisKey, requestHash, action);
		}
		return handleExisting(redisKey, requestHash, responseType, action, allowRetryOnMissingValue);
	}

	private <T> IdempotentResult<T> handleExisting(String redisKey, String requestHash, Class<T> responseType,
			Supplier<T> action, boolean allowRetryOnMissingValue) {
		String raw;
		try {
			raw = redisTemplate.opsForValue().get(redisKey);
		} catch (DataAccessException e) {
			// 키가 이미 있다는 건 같은 요청이 처리 중이거나 끝났다는 뜻이라, 조회 실패로 멱등 없이 action 을
			// 실행하면 중복 주문이 생긴다. 가용성보다 중복 방지를 우선해 진행 중으로 취급한다.
			log.warn("멱등 키 조회 실패, 진행 중으로 취급한다 key={}", redisKey, e);
			throw new BusinessException(ErrorCode.ORDER_REQUEST_IN_PROGRESS);
		}
		if (raw == null) {
			if (allowRetryOnMissingValue) {
				return acquire(redisKey, requestHash, responseType, action, false);
			}
			throw new BusinessException(ErrorCode.ORDER_REQUEST_IN_PROGRESS);
		}
		StoredEntry entry = deserialize(raw);
		if (!entry.requestHash().equals(requestHash)) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
		}
		if (entry.status() == Status.PROCESSING) {
			throw new BusinessException(ErrorCode.ORDER_REQUEST_IN_PROGRESS);
		}
		return new IdempotentResult<>(convert(entry.response(), responseType), true);
	}

	private <T> IdempotentResult<T> runAction(String redisKey, String requestHash, Supplier<T> action) {
		T response;
		try {
			response = action.get();
		} catch (RuntimeException | Error e) {
			deleteQuietly(redisKey);
			throw e;
		}
		completeQuietly(redisKey, requestHash, response);
		return new IdempotentResult<>(response, false);
	}

	private void deleteQuietly(String redisKey) {
		try {
			redisTemplate.delete(redisKey);
		} catch (DataAccessException e) {
			log.warn("멱등 키 삭제 실패 key={}", redisKey, e);
		}
	}

	private <T> void completeQuietly(String redisKey, String requestHash, T response) {
		try {
			JsonNode responseNode = objectMapper.valueToTree(response);
			redisTemplate.opsForValue().set(redisKey, serialize(StoredEntry.completed(requestHash, responseNode)),
					TTL);
		} catch (DataAccessException e) {
			log.warn("멱등 완료 응답 저장 실패, 키는 PROCESSING 상태로 TTL 까지 남는다 key={}", redisKey, e);
		}
	}

	private String buildKey(String scope, Long memberId, String key) {
		return "idem:" + scope + ":" + memberId + ":" + key;
	}

	private String hash(Object request) {
		try {
			byte[] serialized = objectMapper.writeValueAsBytes(request);
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(serialized));
		} catch (JsonProcessingException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("멱등 요청 해시 계산 실패", e);
		}
	}

	private String serialize(StoredEntry entry) {
		try {
			return objectMapper.writeValueAsString(entry);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("멱등 상태 직렬화 실패", e);
		}
	}

	private StoredEntry deserialize(String raw) {
		try {
			return objectMapper.readValue(raw, StoredEntry.class);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("멱등 상태 역직렬화 실패", e);
		}
	}

	private <T> T convert(JsonNode response, Class<T> responseType) {
		try {
			return objectMapper.treeToValue(response, responseType);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("멱등 응답 역직렬화 실패", e);
		}
	}

	private enum Status {
		PROCESSING, COMPLETED
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record StoredEntry(Status status, String requestHash, JsonNode response) {

		static StoredEntry processing(String requestHash) {
			return new StoredEntry(Status.PROCESSING, requestHash, null);
		}

		static StoredEntry completed(String requestHash, JsonNode response) {
			return new StoredEntry(Status.COMPLETED, requestHash, response);
		}
	}
}
