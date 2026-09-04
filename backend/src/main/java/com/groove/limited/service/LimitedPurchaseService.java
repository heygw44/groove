package com.groove.limited.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.config.LimitedProperties;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.service.LimitedDropRedisService.ReserveResult;

import lombok.RequiredArgsConstructor;

/**
 * 한정반 선착순 구매 진입점. Redis 로 선점 경쟁을 거른 뒤에만 DB 트랜잭션(LimitedPurchaseWriter)을 태우므로
 * 이 클래스 자체는 트랜잭션을 걸지 않는다. {@code limited.redis-enabled} 가 false 면 부하 테스트 비교용으로
 * Redis 단계를 건너뛰고 DB 락만으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class LimitedPurchaseService {

	private final LimitedDropRepository limitedDropRepository;
	private final LimitedDropRedisService limitedDropRedisService;
	private final LimitedPurchaseWriter limitedPurchaseWriter;
	private final LimitedProperties limitedProperties;
	private final Clock clock;

	public LimitedPurchaseResponse purchase(Long dropId, Long memberId, Long addressId) {
		LimitedDrop drop = limitedDropRepository.findById(dropId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LIMITED_DROP_NOT_FOUND));
		drop.validatePurchasable(LocalDateTime.now(clock));

		if (!limitedProperties.redisEnabled()) {
			return limitedPurchaseWriter.write(dropId, memberId, addressId);
		}

		ReserveResult reserveResult = limitedDropRedisService.reserve(dropId, memberId);
		validateReserveResult(reserveResult);

		try {
			return limitedPurchaseWriter.write(dropId, memberId, addressId);
		} catch (RuntimeException e) {
			limitedDropRedisService.release(dropId, memberId);
			throw e;
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
}
