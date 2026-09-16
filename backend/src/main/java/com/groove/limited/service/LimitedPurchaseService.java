package com.groove.limited.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.config.LimitedCircuitProperties;
import com.groove.limited.config.LimitedProperties;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedAttemptResult;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.service.LimitedDropRedisService.ReserveResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 한정반 선착순 구매 진입점. Redis 로 선점 경쟁을 거른 뒤에만 DB 트랜잭션(LimitedPurchaseWriter)을 태우므로
 * 이 클래스 자체는 트랜잭션을 걸지 않는다. {@code limited.redis-enabled} 가 false 면 부하 테스트 비교용으로
 * Redis 단계를 건너뛰고 DB 락만으로 처리한다. Redis 장애 시에는 서킷이 OPEN 되어 DB 락 경로로 폴백한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LimitedPurchaseService {

	private final LimitedDropRepository limitedDropRepository;
	private final LimitedDropRedisService limitedDropRedisService;
	private final LimitedPurchaseWriter limitedPurchaseWriter;
	private final LimitedDropSyncService limitedDropSyncService;
	private final LimitedRedisCircuitBreaker limitedRedisCircuitBreaker;
	private final LimitedFallbackGate limitedFallbackGate;
	private final LimitedProperties limitedProperties;
	private final LimitedCircuitProperties limitedCircuitProperties;
	private final Clock clock;

	public LimitedPurchaseResponse purchase(Long dropId, Long memberId, Long addressId) {
		LimitedDrop drop = limitedDropRepository.findById(dropId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LIMITED_DROP_NOT_FOUND));
		try {
			drop.validatePurchasable(LocalDateTime.now(clock));
			if (!limitedProperties.redisEnabled()) {
				return limitedPurchaseWriter.write(dropId, memberId, addressId);
			}
			if (!limitedRedisCircuitBreaker.allowRedis()) {
				return fallback(dropId, memberId, addressId);
			}
			return reserveAndWrite(dropId, memberId, addressId);
		} catch (BusinessException e) {
			recordAttempt(drop, e.getErrorCode());
			throw e;
		}
	}

	private LimitedPurchaseResponse reserveAndWrite(Long dropId, Long memberId, Long addressId) {
		ReserveResult reserveResult;
		try {
			resyncFallbackDrops();
			reserveResult = limitedDropRedisService.reserve(dropId, memberId);
			if (reserveResult == ReserveResult.NOT_INITIALIZED && limitedDropSyncService.rebuildOnce(dropId)) {
				reserveResult = limitedDropRedisService.reserve(dropId, memberId);
			}
			limitedRedisCircuitBreaker.onSuccess();
		} catch (DataAccessException e) {
			limitedRedisCircuitBreaker.onFailure();
			log.warn("한정반 Redis 선점 실패, DB 경로로 폴백 dropId={} memberId={}", dropId, memberId, e);
			return fallback(dropId, memberId, addressId);
		}
		validateReserveResult(reserveResult);

		try {
			return limitedPurchaseWriter.write(dropId, memberId, addressId);
		} catch (RuntimeException e) {
			limitedDropRedisService.release(dropId, memberId);
			throw e;
		}
	}

	/** 서킷이 HALF_OPEN 프로브로 CLOSED 복귀를 시도하는 시점에, 폴백 중 밀린 드롭들을 DB 기준으로 재적재한다. */
	private void resyncFallbackDrops() {
		Set<Long> drops = limitedRedisCircuitBreaker.fallbackDrops();
		if (drops.isEmpty()) {
			return;
		}
		for (Long dropId : drops) {
			limitedDropSyncService.sync(dropId);
		}
		limitedRedisCircuitBreaker.clearFallbackDrops(drops);
	}

	private LimitedPurchaseResponse fallback(Long dropId, Long memberId, Long addressId) {
		if (!limitedCircuitProperties.fallbackEnabled()) {
			throw new BusinessException(ErrorCode.LIMITED_BUSY);
		}
		if (!limitedFallbackGate.tryEnter(dropId)) {
			throw new BusinessException(ErrorCode.LIMITED_BUSY);
		}
		try {
			limitedRedisCircuitBreaker.noteFallback(dropId);
			return limitedPurchaseWriter.write(dropId, memberId, addressId);
		} finally {
			limitedFallbackGate.exit(dropId);
		}
	}

	private void validateReserveResult(ReserveResult result) {
		switch (result) {
			case OK -> {
			}
			case ALREADY -> throw new BusinessException(ErrorCode.LIMITED_ALREADY_PURCHASED);
			case SOLD_OUT -> throw new BusinessException(ErrorCode.LIMITED_SOLD_OUT);
			case NOT_INITIALIZED -> throw new BusinessException(ErrorCode.LIMITED_NOT_OPEN);
		}
	}

	/**
	 * CLOSED 이후에는 기록하지 않는다. 마감 flush 로 이미 지운 Redis 키가 뒤늦은 요청으로 되살아나 고아 키가 되는 것을
	 * 막는다. 서킷이 CLOSED 가 아닐 때(OPEN/HALF_OPEN)도 기록하지 않는다 — 이 실패는 Redis 경쟁이 아니라 DB 폴백
	 * 경로에서 난 것이라 Redis 집계와 성격이 다르다.
	 */
	private void recordAttempt(LimitedDrop drop, ErrorCode errorCode) {
		if (!limitedProperties.redisEnabled() || drop.getStatus() == LimitedDropStatus.CLOSED
				|| !limitedRedisCircuitBreaker.isClosed()) {
			return;
		}
		LimitedAttemptResult.from(errorCode)
				.ifPresent(result -> limitedDropRedisService.recordAttempt(drop.getId(), result));
	}
}
