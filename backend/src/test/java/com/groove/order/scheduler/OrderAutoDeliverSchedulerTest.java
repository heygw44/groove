package com.groove.order.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.order.config.OrderAutoDeliverProperties;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.service.OrderAutoDeliverLock;
import com.groove.order.service.OrderAutoDeliverService;

@ExtendWith(MockitoExtension.class)
class OrderAutoDeliverSchedulerTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private OrderItemRepository orderItemRepository;

	@Mock
	private OrderAutoDeliverService orderAutoDeliverService;

	@Mock
	private OrderAutoDeliverLock orderAutoDeliverLock;

	@Mock
	private ShutdownSignal shutdownSignal;

	private OrderAutoDeliverScheduler scheduler;

	private LocalDateTime now;
	private LocalDateTime cutoff;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		now = LocalDateTime.now(clock);
		OrderAutoDeliverProperties properties = new OrderAutoDeliverProperties(5, Duration.ofMinutes(5), 200);
		cutoff = now.minusDays(properties.days());
		scheduler = new OrderAutoDeliverScheduler(orderItemRepository, orderAutoDeliverService, orderAutoDeliverLock,
				properties, shutdownSignal, clock);
	}

	@Nested
	@DisplayName("deliverOrders()")
	class DeliverOrders {

		@Test
		@DisplayName("시작 시 셧다운 중이면 락도 잡지 않는다")
		void doesNotAcquireLockWhenShuttingDownAtStart() {
			// given
			given(shutdownSignal.isShuttingDown()).willReturn(true);

			// when
			scheduler.deliverOrders();

			// then
			verify(orderAutoDeliverLock, never()).runExclusively(any());
		}

		@Test
		@DisplayName("락 획득에 실패하면 대상 조회조차 하지 않는다")
		void doesNothingWhenLockAcquisitionFails() {
			// given
			given(shutdownSignal.isShuttingDown()).willReturn(false);
			given(orderAutoDeliverLock.runExclusively(any())).willReturn(false);

			// when
			scheduler.deliverOrders();

			// then
			verify(orderItemRepository, never()).findIdsByStatusAndShippedAtBefore(any(), any(), any());
		}

		@Test
		@DisplayName("대상이 없으면 서비스를 호출하지 않는다")
		void doesNotCallServiceWhenNoCandidates() {
			// given
			stubLockToRunTask();
			given(orderItemRepository.findIdsByStatusAndShippedAtBefore(eq(OrderItemStatus.SHIPPING), eq(cutoff),
					any())).willReturn(List.of());

			// when
			scheduler.deliverOrders();

			// then
			verify(orderAutoDeliverService, never()).deliver(anyLong(), any(), any());
		}

		@Test
		@DisplayName("한 건이 실패해도 나머지 후보는 계속 처리한다")
		void continuesProcessingWhenOneItemFails() {
			// given
			stubLockToRunTask();
			given(orderItemRepository.findIdsByStatusAndShippedAtBefore(eq(OrderItemStatus.SHIPPING), eq(cutoff),
					any())).willReturn(List.of(1L, 2L, 3L));
			given(orderAutoDeliverService.deliver(1L, cutoff, now)).willReturn(false);
			given(orderAutoDeliverService.deliver(2L, cutoff, now)).willThrow(new RuntimeException("boom"));
			given(orderAutoDeliverService.deliver(3L, cutoff, now)).willReturn(true);

			// when
			scheduler.deliverOrders();

			// then
			verify(orderAutoDeliverService).deliver(1L, cutoff, now);
			verify(orderAutoDeliverService).deliver(2L, cutoff, now);
			verify(orderAutoDeliverService).deliver(3L, cutoff, now);
		}

		@Test
		@DisplayName("첫 건 처리 후 셧다운 신호가 오면 두 번째 건을 처리하지 않는다")
		void stopsProcessingWhenShutdownSignaledMidLoop() {
			// given
			stubLockToRunTask();
			given(shutdownSignal.isShuttingDown()).willReturn(false, false, true);
			given(orderItemRepository.findIdsByStatusAndShippedAtBefore(eq(OrderItemStatus.SHIPPING), eq(cutoff),
					any())).willReturn(List.of(1L, 2L));
			given(orderAutoDeliverService.deliver(1L, cutoff, now)).willReturn(true);

			// when
			scheduler.deliverOrders();

			// then
			verify(orderAutoDeliverService).deliver(1L, cutoff, now);
			verify(orderAutoDeliverService, never()).deliver(eq(2L), any(), any());
		}
	}

	private void stubLockToRunTask() {
		given(orderAutoDeliverLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}
}
