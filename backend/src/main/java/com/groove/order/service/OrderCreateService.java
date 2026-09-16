package com.groove.order.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.idempotency.IdempotentExecutor;
import com.groove.global.idempotency.IdempotentResult;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderCreateResponse;

import lombok.RequiredArgsConstructor;

/**
 * 주문 생성 진입점. {@link OrderService#create}는 자체 {@code @Transactional} 로 커밋되는 프록시 호출이라,
 * 이 클래스에 읽기 전용 트랜잭션을 걸면 그 트랜잭션의 커밋이 멱등 응답 저장보다 늦어지고(재요청이 아직
 * COMMITTED 되지 않은 주문을 못 봄) 쓰기 메서드가 바깥 readOnly 트랜잭션에 합류해 깨진다. 그래서 이
 * 클래스는 트랜잭션을 걸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OrderCreateService {

	private static final String SCOPE = "order";

	private final IdempotentExecutor idempotentExecutor;
	private final OrderService orderService;

	public IdempotentResult<OrderCreateResponse> create(Long memberId, String idempotencyKey,
			OrderCreateRequest request) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return new IdempotentResult<>(orderService.create(memberId, request), false);
		}
		validateKeyFormat(idempotencyKey);
		return idempotentExecutor.execute(SCOPE, memberId, idempotencyKey, request, OrderCreateResponse.class,
				() -> orderService.create(memberId, request));
	}

	private void validateKeyFormat(String idempotencyKey) {
		try {
			UUID.fromString(idempotencyKey);
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
	}
}
