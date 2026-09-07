package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

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
import com.groove.limited.entity.LimitedAttemptResult;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStat;
import com.groove.limited.repository.LimitedDropStatRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class LimitedDropStatFlusherTest {

	@Mock
	LimitedDropStatRepository limitedDropStatRepository;

	@Mock
	LimitedDropRedisService limitedDropRedisService;

	LimitedDropStatFlusher limitedDropStatFlusher;

	LimitedDrop drop;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-07T01:00:00Z"), ZoneId.of("Asia/Seoul"));
		limitedDropStatFlusher = new LimitedDropStatFlusher(limitedDropStatRepository, limitedDropRedisService, clock);
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 100L);
		drop = LimitedDropFixture.withId(LimitedDropFixture.open(product, 100), 1L);
	}

	@Nested
	@DisplayName("flushAndClear()")
	class FlushAndClear {

		@Test
		@DisplayName("집계가 비어 있으면 통계를 저장하지 않고 Redis 키만 지운다")
		void clearsRedisKeysWithoutSavingWhenCountsEmpty() {
			// given
			given(limitedDropRedisService.getAttemptsForFlush(1L)).willReturn(Map.of());

			// when
			limitedDropStatFlusher.flushAndClear(drop);

			// then
			verify(limitedDropStatRepository, never()).findByDropId(any());
			verify(limitedDropStatRepository, never()).save(any());
			verify(limitedDropRedisService).clear(1L);
		}

		@Test
		@DisplayName("집계가 있고 기존 통계가 없으면 새로 저장하고 Redis 키를 지운다")
		void savesNewStatWhenCountsPresentAndNoExistingStat() {
			// given
			Map<LimitedAttemptResult, Long> counts = Map.of(LimitedAttemptResult.SOLD_OUT, 3L);
			given(limitedDropRedisService.getAttemptsForFlush(1L)).willReturn(counts);
			given(limitedDropStatRepository.findByDropId(1L)).willReturn(Optional.empty());

			// when
			limitedDropStatFlusher.flushAndClear(drop);

			// then
			verify(limitedDropStatRepository).save(any(LimitedDropStat.class));
			verify(limitedDropRedisService).clear(1L);
		}

		@Test
		@DisplayName("집계가 있고 기존 통계가 있으면 새로 저장하지 않고 기존 통계를 갱신한다")
		void updatesExistingStatWithoutSavingWhenExistingStatPresent() {
			// given
			LimitedDropStat existing = LimitedDropStat.of(drop, Map.of(LimitedAttemptResult.SOLD_OUT, 1L),
					LocalDateTime.now());
			Map<LimitedAttemptResult, Long> counts = Map.of(LimitedAttemptResult.CLOSED, 5L);
			given(limitedDropRedisService.getAttemptsForFlush(1L)).willReturn(counts);
			given(limitedDropStatRepository.findByDropId(1L)).willReturn(Optional.of(existing));

			// when
			limitedDropStatFlusher.flushAndClear(drop);

			// then
			assertThat(existing.getClosedCount()).isEqualTo(5);
			assertThat(existing.getSoldOutCount()).isZero();
			verify(limitedDropStatRepository, never()).save(any());
			verify(limitedDropRedisService).clear(1L);
		}
	}
}
