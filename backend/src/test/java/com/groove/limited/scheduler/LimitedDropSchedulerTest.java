package com.groove.limited.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
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

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.service.LimitedDropScheduleService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class LimitedDropSchedulerTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private LimitedDropRepository limitedDropRepository;

	@Mock
	private LimitedDropScheduleService scheduleService;

	private LimitedDropScheduler scheduler;

	private LocalDateTime now;
	private Product product;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		now = LocalDateTime.now(clock);
		scheduler = new LimitedDropScheduler(limitedDropRepository, scheduleService, clock);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	private LimitedDrop dropWithId(Long id) {
		return LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), id);
	}

	@Nested
	@DisplayName("run()")
	class Run {

		@Test
		@DisplayName("오픈 대상 하나가 실패해도 나머지 대상은 계속 처리한다")
		void continuesWhenOneOpenFails() {
			// given
			given(limitedDropRepository.findAllByStatusAndOpenAtLessThanEqual(eq(LimitedDropStatus.SCHEDULED),
					any())).willReturn(List.of(dropWithId(1L), dropWithId(2L), dropWithId(3L)));
			given(limitedDropRepository.findAllByStatusInAndCloseAtLessThanEqual(any(), any()))
					.willReturn(List.of());
			given(scheduleService.open(1L, now)).willReturn(true);
			given(scheduleService.open(2L, now)).willThrow(new RuntimeException("boom"));
			given(scheduleService.open(3L, now)).willReturn(true);

			// when
			scheduler.run();

			// then
			verify(scheduleService).open(1L, now);
			verify(scheduleService).open(2L, now);
			verify(scheduleService).open(3L, now);
		}

		@Test
		@DisplayName("오픈 대상과 마감 대상을 한 번의 실행에서 모두 처리한다")
		void processesBothOpenAndCloseCandidates() {
			// given
			given(limitedDropRepository.findAllByStatusAndOpenAtLessThanEqual(eq(LimitedDropStatus.SCHEDULED),
					any())).willReturn(List.of(dropWithId(1L)));
			given(limitedDropRepository.findAllByStatusInAndCloseAtLessThanEqual(any(), any()))
					.willReturn(List.of(dropWithId(2L)));
			given(scheduleService.open(1L, now)).willReturn(true);
			given(scheduleService.close(2L, now)).willReturn(true);

			// when
			scheduler.run();

			// then
			verify(scheduleService).open(1L, now);
			verify(scheduleService).close(2L, now);
		}

		@Test
		@DisplayName("오픈·마감 대상이 없으면 서비스를 호출하지 않는다")
		void doesNotCallServiceWhenNoCandidates() {
			// given
			given(limitedDropRepository.findAllByStatusAndOpenAtLessThanEqual(eq(LimitedDropStatus.SCHEDULED),
					any())).willReturn(List.of());
			given(limitedDropRepository.findAllByStatusInAndCloseAtLessThanEqual(any(), any()))
					.willReturn(List.of());

			// when
			scheduler.run();

			// then
			verify(scheduleService, never()).open(any(), any());
			verify(scheduleService, never()).close(any(), any());
		}
	}
}
