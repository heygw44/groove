package com.groove.recommend.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.recommend.service.BoughtTogetherAggregator;

@ExtendWith(MockitoExtension.class)
class BoughtTogetherSchedulerTest {

	@Mock
	private BoughtTogetherAggregator boughtTogetherAggregator;

	private BoughtTogetherScheduler boughtTogetherScheduler;

	@BeforeEach
	void setUp() {
		boughtTogetherScheduler = new BoughtTogetherScheduler(boughtTogetherAggregator);
	}

	@Nested
	@DisplayName("refresh()")
	class Refresh {

		@Test
		@DisplayName("집계기(refresh)를 위임 호출한다")
		void delegatesToAggregator() {
			// given
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
			given(boughtTogetherAggregator.refresh()).willThrow(new RuntimeException("boom"));

			// when & then
			assertThatCode(() -> boughtTogetherScheduler.refresh()).doesNotThrowAnyException();
		}
	}
}
