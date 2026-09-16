package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.config.LimitedReconcileProperties;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class LimitedDropSyncServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final Long DROP_ID = 1L;

	@Mock
	private LimitedDropRepository limitedDropRepository;

	@Mock
	private LimitedPurchaseRepository limitedPurchaseRepository;

	@Mock
	private LimitedDropRedisService limitedDropRedisService;

	private Clock clock;

	private LimitedDropSyncService limitedDropSyncService;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-14T03:00:00Z"), ZONE);
		LimitedReconcileProperties properties = new LimitedReconcileProperties(Duration.ofSeconds(30),
				Duration.ofSeconds(30));
		limitedDropSyncService = new LimitedDropSyncService(limitedDropRepository, limitedPurchaseRepository,
				limitedDropRedisService, properties, clock);
	}

	private LimitedDrop dropWithStatus(LimitedDropStatus status) {
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 100L);
		LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.open(product, 10), DROP_ID);
		LimitedDropFixture.withStatus(drop, status);
		return drop;
	}

	@Nested
	@DisplayName("sync()")
	class Sync {

		@Test
		@DisplayName("드롭이 없으면 Redis 를 건드리지 않고 empty 를 반환한다")
		void returnsEmptyWhenDropNotFound() {
			// given
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.empty());

			// when
			Optional<LimitedSyncResult> result = limitedDropSyncService.sync(DROP_ID);

			// then
			assertThat(result).isEmpty();
			verifyNoInteractions(limitedDropRedisService);
			verifyNoInteractions(limitedPurchaseRepository);
		}

		@Test
		@DisplayName("SCHEDULED 상태면 Redis 를 건드리지 않고 empty 를 반환한다")
		void returnsEmptyWhenScheduled() {
			// given
			given(limitedDropRepository.findByIdForUpdate(DROP_ID))
					.willReturn(Optional.of(dropWithStatus(LimitedDropStatus.SCHEDULED)));

			// when
			Optional<LimitedSyncResult> result = limitedDropSyncService.sync(DROP_ID);

			// then
			assertThat(result).isEmpty();
			verifyNoInteractions(limitedDropRedisService);
		}

		@Test
		@DisplayName("CLOSED 상태면 Redis 를 건드리지 않고 empty 를 반환한다")
		void returnsEmptyWhenClosed() {
			// given
			given(limitedDropRepository.findByIdForUpdate(DROP_ID))
					.willReturn(Optional.of(dropWithStatus(LimitedDropStatus.CLOSED)));

			// when
			Optional<LimitedSyncResult> result = limitedDropSyncService.sync(DROP_ID);

			// then
			assertThat(result).isEmpty();
			verifyNoInteractions(limitedDropRedisService);
		}

		@Test
		@DisplayName("OPEN 상태면 DB 잔여 수량·구매자·cutoff 로 Redis sync 를 호출한다")
		void callsRedisSyncWhenOpen() {
			// given
			LimitedDrop drop = dropWithStatus(LimitedDropStatus.OPEN);
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.of(drop));
			given(limitedPurchaseRepository.findMemberIdsByDropId(DROP_ID)).willReturn(List.of(1L, 2L));
			LimitedSyncResult syncResult = new LimitedSyncResult(5, 8, true, 1, 1);
			given(limitedDropRedisService.sync(eq(DROP_ID), eq(drop.remainingQuantity()), eq(List.of(1L, 2L)),
					anyLong())).willReturn(syncResult);

			// when
			Optional<LimitedSyncResult> result = limitedDropSyncService.sync(DROP_ID);

			// then
			assertThat(result).contains(syncResult);
			long expectedCutoff = clock.millis() - Duration.ofSeconds(30).toMillis();
			verify(limitedDropRedisService).sync(DROP_ID, drop.remainingQuantity(), List.of(1L, 2L), expectedCutoff);
		}

		@Test
		@DisplayName("SOLD_OUT 상태여도 Redis sync 를 호출한다")
		void callsRedisSyncWhenSoldOut() {
			// given
			LimitedDrop drop = dropWithStatus(LimitedDropStatus.SOLD_OUT);
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.of(drop));
			given(limitedPurchaseRepository.findMemberIdsByDropId(DROP_ID)).willReturn(List.of());
			given(limitedDropRedisService.sync(any(), anyInt(), anyCollection(), anyLong()))
					.willReturn(new LimitedSyncResult(0, 0, false, 0, 0));

			// when
			Optional<LimitedSyncResult> result = limitedDropSyncService.sync(DROP_ID);

			// then
			assertThat(result).isPresent();
			verify(limitedDropRedisService).sync(any(), anyInt(), anyCollection(), anyLong());
		}
	}

	@Nested
	@DisplayName("rebuildOnce()")
	class RebuildOnce {

		@Test
		@DisplayName("락을 못 잡으면 sync 를 호출하지 않고 false 를 반환한다")
		void returnsFalseWhenLockNotAcquired() {
			// given
			given(limitedDropRedisService.tryLockRebuild(DROP_ID)).willReturn(false);

			// when
			boolean result = limitedDropSyncService.rebuildOnce(DROP_ID);

			// then
			assertThat(result).isFalse();
			verify(limitedDropRepository, never()).findByIdForUpdate(any());
			verify(limitedDropRedisService, never()).unlockRebuild(any());
		}

		@Test
		@DisplayName("락을 잡으면 sync 를 호출하고 락을 해제한 뒤 true 를 반환한다")
		void syncsAndUnlocksWhenLockAcquired() {
			// given
			given(limitedDropRedisService.tryLockRebuild(DROP_ID)).willReturn(true);
			given(limitedDropRepository.findByIdForUpdate(DROP_ID)).willReturn(Optional.empty());

			// when
			boolean result = limitedDropSyncService.rebuildOnce(DROP_ID);

			// then
			assertThat(result).isTrue();
			verify(limitedDropRepository).findByIdForUpdate(DROP_ID);
			verify(limitedDropRedisService).unlockRebuild(DROP_ID);
		}
	}
}
