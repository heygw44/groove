package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.OrderFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.idempotency.IdempotentExecutor;
import com.groove.global.idempotency.IdempotentResult;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderCreateResponse;

@ExtendWith(MockitoExtension.class)
class OrderCreateServiceTest {

	@Mock
	private IdempotentExecutor idempotentExecutor;

	@Mock
	private OrderService orderService;

	@InjectMocks
	private OrderCreateService orderCreateService;

	private OrderCreateResponse sampleResponse() {
		return new OrderCreateResponse(1L, "20260903-TESTAB12", new BigDecimal("90000"), BigDecimal.ZERO,
				new BigDecimal("90000"), null);
	}

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("키가 null 이면 executor 를 거치지 않고 바로 생성한다")
		void skipsExecutorWhenKeyIsNull() {
			// given
			OrderCreateRequest request = OrderFixture.directRequest(1L, 1, 10L);
			OrderCreateResponse response = sampleResponse();
			given(orderService.create(1L, request)).willReturn(response);

			// when
			IdempotentResult<OrderCreateResponse> result = orderCreateService.create(1L, null, request);

			// then
			assertThat(result.replayed()).isFalse();
			assertThat(result.response()).isEqualTo(response);
			verifyNoInteractions(idempotentExecutor);
		}

		@Test
		@DisplayName("키가 빈 문자열이면 executor 를 거치지 않고 바로 생성한다")
		void skipsExecutorWhenKeyIsBlank() {
			// given
			OrderCreateRequest request = OrderFixture.directRequest(1L, 1, 10L);
			OrderCreateResponse response = sampleResponse();
			given(orderService.create(1L, request)).willReturn(response);

			// when
			IdempotentResult<OrderCreateResponse> result = orderCreateService.create(1L, "   ", request);

			// then
			assertThat(result.replayed()).isFalse();
			assertThat(result.response()).isEqualTo(response);
			verifyNoInteractions(idempotentExecutor);
		}

		@Test
		@DisplayName("키 형식이 UUID 가 아니면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenKeyFormatIsInvalid() {
			// given
			OrderCreateRequest request = OrderFixture.directRequest(1L, 1, 10L);

			// when & then
			assertThatThrownBy(() -> orderCreateService.create(1L, "not-a-uuid", request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
			verifyNoInteractions(idempotentExecutor);
			verifyNoInteractions(orderService);
		}

		@Test
		@DisplayName("키가 유효한 UUID 면 executor 로 위임한다")
		void delegatesToExecutorWhenKeyIsValid() {
			// given
			String key = UUID.randomUUID().toString();
			OrderCreateRequest request = OrderFixture.directRequest(1L, 1, 10L);
			OrderCreateResponse response = sampleResponse();
			given(idempotentExecutor.execute(eq("order"), eq(1L), eq(key), eq(request),
					eq(OrderCreateResponse.class), any())).willReturn(new IdempotentResult<>(response, true));

			// when
			IdempotentResult<OrderCreateResponse> result = orderCreateService.create(1L, key, request);

			// then
			assertThat(result.replayed()).isTrue();
			assertThat(result.response()).isEqualTo(response);
			verifyNoInteractions(orderService);
		}
	}
}
