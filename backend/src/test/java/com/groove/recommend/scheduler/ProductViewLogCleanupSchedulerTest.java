package com.groove.recommend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.recommend.service.ProductViewLogCleanupService;
import com.groove.recommend.service.ViewLogCleanupLock;

@ExtendWith(MockitoExtension.class)
class ProductViewLogCleanupSchedulerTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private ProductViewLogCleanupService productViewLogCleanupService;

	@Mock
	private ViewLogCleanupLock viewLogCleanupLock;

	private ProductViewLogCleanupScheduler scheduler;

	private Clock clock;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		scheduler = new ProductViewLogCleanupScheduler(productViewLogCleanupService, viewLogCleanupLock, clock);
	}

	private void stubLockToRunTask() {
		given(viewLogCleanupLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}

	@Nested
	@DisplayName("cleanUp()")
	class CleanUp {

		@Test
		@DisplayName("락 획득에 실패하면 삭제를 호출하지 않는다")
		void doesNothingWhenLockAcquisitionFails() {
			// given
			given(viewLogCleanupLock.runExclusively(any())).willReturn(false);

			// when
			scheduler.cleanUp();

			// then
			verify(productViewLogCleanupService, never()).deleteBatch(any(), any(Integer.class));
		}

		@Test
		@DisplayName("90일 전 threshold 로 배치 삭제를 호출한다")
		void deletesWithNinetyDayThreshold() {
			// given
			stubLockToRunTask();
			LocalDateTime expectedThreshold = LocalDateTime.now(clock)
					.minusDays(ProductViewLogCleanupScheduler.RETENTION_DAYS);
			given(productViewLogCleanupService.deleteBatch(any(), eq(ProductViewLogCleanupScheduler.BATCH_SIZE)))
					.willReturn(0);
			ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

			// when
			scheduler.cleanUp();

			// then
			verify(productViewLogCleanupService).deleteBatch(thresholdCaptor.capture(),
					eq(ProductViewLogCleanupScheduler.BATCH_SIZE));
			assertThat(thresholdCaptor.getValue()).isEqualTo(expectedThreshold);
		}

		@Test
		@DisplayName("삭제 건수가 배치 크기보다 작으면 루프를 멈춘다")
		void stopsLoopWhenDeletedCountIsBelowBatchSize() {
			// given
			stubLockToRunTask();
			given(productViewLogCleanupService.deleteBatch(any(), eq(ProductViewLogCleanupScheduler.BATCH_SIZE)))
					.willReturn(ProductViewLogCleanupScheduler.BATCH_SIZE - 1);

			// when
			scheduler.cleanUp();

			// then
			verify(productViewLogCleanupService, times(1)).deleteBatch(any(),
					eq(ProductViewLogCleanupScheduler.BATCH_SIZE));
		}

		@Test
		@DisplayName("배치가 가득 차면 다음 배치를 이어서 호출한다")
		void continuesToNextBatchWhenFull() {
			// given
			stubLockToRunTask();
			given(productViewLogCleanupService.deleteBatch(any(), eq(ProductViewLogCleanupScheduler.BATCH_SIZE)))
					.willReturn(ProductViewLogCleanupScheduler.BATCH_SIZE, ProductViewLogCleanupScheduler.BATCH_SIZE,
							0);

			// when
			scheduler.cleanUp();

			// then
			verify(productViewLogCleanupService, times(3)).deleteBatch(any(),
					eq(ProductViewLogCleanupScheduler.BATCH_SIZE));
		}

		@Test
		@DisplayName("서비스가 예외를 던져도 예외를 전파하지 않는다")
		void doesNotPropagateExceptionFromService() {
			// given
			stubLockToRunTask();
			given(productViewLogCleanupService.deleteBatch(any(), eq(ProductViewLogCleanupScheduler.BATCH_SIZE)))
					.willThrow(new RuntimeException("boom"));

			// when & then
			assertThatCode(() -> scheduler.cleanUp()).doesNotThrowAnyException();
		}
	}
}
