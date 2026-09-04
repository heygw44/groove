package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class LimitedDropScheduleServiceTest {

	private static final Long DROP_ID = 10L;
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private LimitedDropRepository limitedDropRepository;

	@Mock
	private LimitedDropRedisService limitedDropRedisService;

	private LimitedDropScheduleService scheduleService;

	private LocalDateTime now;
	private Product product;

	@BeforeEach
	void setUp() {
		scheduleService = new LimitedDropScheduleService(limitedDropRepository, limitedDropRedisService);
		Clock clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		now = LocalDateTime.now(clock);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	private LimitedDrop scheduledDrop() {
		LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), DROP_ID);
		LimitedDropFixture.withOpenAt(drop, now.minusMinutes(1));
		LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
		return drop;
	}

	@Nested
	@DisplayName("open()")
	class Open {

		@Test
		@DisplayName("SCHEDULED 이고 오픈 시각이 지났으면 OPEN 으로 바꾸고 Redis 재고를 초기화한다")
		void opensAndInitStockWhenScheduledAndPastOpenAt() {
			// given
			LimitedDrop drop = scheduledDrop();
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.of(drop));

			// when
			boolean result = scheduleService.open(DROP_ID, now);

			// then
			assertThat(result).isTrue();
			assertThat(drop.getStatus()).isEqualTo(LimitedDropStatus.OPEN);
			verify(limitedDropRedisService).initStock(DROP_ID, drop.remainingQuantity());
		}

		@Test
		@DisplayName("이미 OPEN 이면 false 를 반환하고 Redis 를 건드리지 않는다")
		void returnsFalseWhenAlreadyOpen() {
			// given
			LimitedDrop drop = scheduledDrop();
			drop.open();
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.of(drop));

			// when
			boolean result = scheduleService.open(DROP_ID, now);

			// then
			assertThat(result).isFalse();
			verify(limitedDropRedisService, never()).initStock(anyLong(), anyInt());
		}

		@Test
		@DisplayName("오픈 시각이 아직 안 지났으면 false 를 반환한다")
		void returnsFalseWhenOpenAtInFuture() {
			// given
			LimitedDrop drop = scheduledDrop();
			LimitedDropFixture.withOpenAt(drop, now.plusMinutes(1));
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.of(drop));

			// when
			boolean result = scheduleService.open(DROP_ID, now);

			// then
			assertThat(result).isFalse();
			verify(limitedDropRedisService, never()).initStock(anyLong(), anyInt());
		}

		@Test
		@DisplayName("드롭을 찾을 수 없으면 false 를 반환한다")
		void returnsFalseWhenNotFound() {
			// given
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.empty());

			// when
			boolean result = scheduleService.open(DROP_ID, now);

			// then
			assertThat(result).isFalse();
		}

		@Test
		@DisplayName("Redis 장애면 예외가 그대로 전파된다")
		void propagatesExceptionWhenRedisFails() {
			// given
			LimitedDrop drop = scheduledDrop();
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.initStock(anyLong(), anyInt()))
					.willThrow(new DataAccessResourceFailureException("redis down"));

			// when & then
			assertThatThrownBy(() -> scheduleService.open(DROP_ID, now))
					.isInstanceOf(DataAccessResourceFailureException.class);
		}
	}

	@Nested
	@DisplayName("close()")
	class Close {

		@Test
		@DisplayName("OPEN 이고 마감 시각이 지났으면 CLOSED 로 바꾸고 Redis 를 지운다")
		void closesAndClearsRedisWhenOpenAndPastCloseAt() {
			// given
			LimitedDrop drop = scheduledDrop();
			drop.open();
			LimitedDropFixture.withCloseAt(drop, now.minusMinutes(1));
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.of(drop));

			// when
			boolean result = scheduleService.close(DROP_ID, now);

			// then
			assertThat(result).isTrue();
			assertThat(drop.getStatus()).isEqualTo(LimitedDropStatus.CLOSED);
			verify(limitedDropRedisService).clear(DROP_ID);
		}

		@Test
		@DisplayName("SOLD_OUT 이고 마감 시각이 지났으면 CLOSED 로 바뀐다")
		void closesWhenSoldOutAndPastCloseAt() {
			// given
			LimitedDrop drop = scheduledDrop();
			drop.open();
			drop.recordSale(drop.getTotalQuantity());
			LimitedDropFixture.withCloseAt(drop, now.minusMinutes(1));
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.of(drop));

			// when
			boolean result = scheduleService.close(DROP_ID, now);

			// then
			assertThat(result).isTrue();
			assertThat(drop.getStatus()).isEqualTo(LimitedDropStatus.CLOSED);
		}

		@Test
		@DisplayName("마감 시각이 아직 안 지났으면 false 를 반환한다")
		void returnsFalseWhenCloseAtInFuture() {
			// given
			LimitedDrop drop = scheduledDrop();
			drop.open();
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.of(drop));

			// when
			boolean result = scheduleService.close(DROP_ID, now);

			// then
			assertThat(result).isFalse();
			verify(limitedDropRedisService, never()).clear(any());
		}

		@Test
		@DisplayName("이미 CLOSED 면 false 를 반환한다")
		void returnsFalseWhenAlreadyClosed() {
			// given
			LimitedDrop drop = scheduledDrop();
			drop.open();
			LimitedDropFixture.withCloseAt(drop, now.minusMinutes(1));
			drop.close();
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.of(drop));

			// when
			boolean result = scheduleService.close(DROP_ID, now);

			// then
			assertThat(result).isFalse();
			verify(limitedDropRedisService, never()).clear(any());
		}
	}
}
