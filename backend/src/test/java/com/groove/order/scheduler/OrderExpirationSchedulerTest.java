package com.groove.order.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedRelease;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderExpirationService;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderExpirationService orderExpirationService;

	@Mock
	private LimitedDropRedisService limitedDropRedisService;

	private OrderExpirationScheduler scheduler;

	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		now = LocalDateTime.now(clock);
		scheduler = new OrderExpirationScheduler(orderRepository, orderExpirationService, limitedDropRedisService,
				clock);
	}

	@Nested
	@DisplayName("expireOrders()")
	class ExpireOrders {

		@Test
		@DisplayName("만료 대상이 없으면 서비스를 호출하지 않는다")
		void doesNotCallServiceWhenNoCandidates() {
			// given
			given(orderRepository.findIdsByStatusAndExpiresAtBefore(eq(OrderStatus.PENDING), any(), any()))
					.willReturn(List.of());

			// when
			scheduler.expireOrders();

			// then
			verify(orderExpirationService, never()).expire(anyLong(), any());
		}

		@Test
		@DisplayName("한 건이 실패해도 나머지 후보는 계속 처리한다")
		void continuesProcessingWhenOneOrderFails() {
			// given
			given(orderRepository.findIdsByStatusAndExpiresAtBefore(eq(OrderStatus.PENDING), any(), any()))
					.willReturn(List.of(1L, 2L, 3L));
			given(orderExpirationService.expire(1L, now)).willReturn(Optional.empty());
			given(orderExpirationService.expire(2L, now)).willThrow(new RuntimeException("boom"));
			given(orderExpirationService.expire(3L, now)).willReturn(Optional.empty());

			// when
			scheduler.expireOrders();

			// then
			verify(orderExpirationService).expire(1L, now);
			verify(orderExpirationService).expire(2L, now);
			verify(orderExpirationService).expire(3L, now);
		}

		@Test
		@DisplayName("한정반 선점 정보가 반환되면 Redis 선점을 해제한다")
		void releasesLimitedDropReservationWhenPresent() {
			// given
			LimitedRelease release = new LimitedRelease(10L, 20L);
			given(orderRepository.findIdsByStatusAndExpiresAtBefore(eq(OrderStatus.PENDING), any(), any()))
					.willReturn(List.of(1L));
			given(orderExpirationService.expire(1L, now)).willReturn(Optional.of(release));

			// when
			scheduler.expireOrders();

			// then
			verify(limitedDropRedisService).release(10L, 20L);
		}

		@Test
		@DisplayName("한정반 선점 정보가 없으면 Redis 를 건드리지 않는다")
		void skipsRedisReleaseWhenEmpty() {
			// given
			given(orderRepository.findIdsByStatusAndExpiresAtBefore(eq(OrderStatus.PENDING), any(), any()))
					.willReturn(List.of(1L));
			given(orderExpirationService.expire(1L, now)).willReturn(Optional.empty());

			// when
			scheduler.expireOrders();

			// then
			verify(limitedDropRedisService, never()).release(any(), any());
		}
	}
}
