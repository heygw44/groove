package com.groove.recommend.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.recommend.service.BoughtTogetherAggregator;
import com.groove.recommend.service.BoughtTogetherLock;

@ExtendWith(MockitoExtension.class)
class BoughtTogetherSchedulerTest {

	@Mock
	private BoughtTogetherAggregator boughtTogetherAggregator;

	@Mock
	private BoughtTogetherLock boughtTogetherLock;

	private BoughtTogetherScheduler boughtTogetherScheduler;

	@BeforeEach
	void setUp() {
		boughtTogetherScheduler = new BoughtTogetherScheduler(boughtTogetherAggregator, boughtTogetherLock);
	}

	private void stubLockToRunTask() {
		given(boughtTogetherLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}

	@Nested
	@DisplayName("refresh()")
	class Refresh {

		@Test
		@DisplayName("락 획득에 실패하면 집계기를 호출하지 않는다")
		void doesNothingWhenLockAcquisitionFails() {
			// given
			given(boughtTogetherLock.runExclusively(any())).willReturn(false);

			// when
			boughtTogetherScheduler.refresh();

			// then
			verify(boughtTogetherAggregator, never()).refresh();
		}

		@Test
		@DisplayName("집계기(refresh)를 위임 호출한다")
		void delegatesToAggregator() {
			// given
			stubLockToRunTask();
			given(boughtTogetherAggregator.refresh()).willReturn(3);

			// when
			boughtTogetherScheduler.refresh();

			// then
			verify(boughtTogetherAggregator).refresh();
		}

		@Test
		@DisplayName("집계기가 예외를 던져도 예외를 전파하지 않는다")
		void doesNotPropagateExceptionFromAggregator() {
			// given
			stubLockToRunTask();
			given(boughtTogetherAggregator.refresh()).willThrow(new RuntimeException("boom"));

			// when & then
			assertThatCode(() -> boughtTogetherScheduler.refresh()).doesNotThrowAnyException();
		}
	}
}
