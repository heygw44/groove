package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.config.LimitedCircuitProperties;
import com.groove.limited.config.LimitedProperties;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedAttemptResult;
import com.groove.limited.entity.LimitedDropStatus;

@ExtendWith(MockitoExtension.class)
class LimitedPurchaseServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final Long PRODUCT_ID = 100L;

	@Mock
	private LimitedDropMetaCache limitedDropMetaCache;

	@Mock
	private LimitedDropRedisService limitedDropRedisService;

	@Mock
	private LimitedPurchaseWriter limitedPurchaseWriter;

	@Mock
	private LimitedDropSyncService limitedDropSyncService;

	@Mock
	private LimitedRedisCircuitBreaker limitedRedisCircuitBreaker;

	@Mock
	private LimitedFallbackGate limitedFallbackGate;

	private Clock clock;

	private LimitedCircuitProperties limitedCircuitProperties;

	private LimitedPurchaseService limitedPurchaseService;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		limitedCircuitProperties = new LimitedCircuitProperties(5, Duration.ofSeconds(10), 5, true);
		// 서킷이 정상(CLOSED)인 대부분의 테스트를 위한 기본값. 서킷·폴백을 다루는 테스트에서만 덮어쓴다.
		lenient().when(limitedRedisCircuitBreaker.allowRedis()).thenReturn(true);
		lenient().when(limitedRedisCircuitBreaker.isClosed()).thenReturn(true);
		limitedPurchaseService = new LimitedPurchaseService(limitedDropMetaCache, limitedDropRedisService,
				limitedPurchaseWriter, limitedDropSyncService, limitedRedisCircuitBreaker, limitedFallbackGate,
				new LimitedProperties(true, Duration.ofSeconds(3)), limitedCircuitProperties, clock);
	}

	@Nested
	@DisplayName("purchase()")
	class Purchase {

		@Test
		@DisplayName("메타가 오픈 전이면 캐시를 다시 읽어도 거절돼 Redis 를 호출하지 않고 LIMITED_NOT_OPEN 예외를 던진다")
		void throwsWhenNotOpenYet() {
			// given
			LimitedDropMeta meta = scheduledMeta(1L);
			given(limitedDropMetaCache.get(1L)).willReturn(Optional.of(meta));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(1L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_NOT_OPEN);
			verify(limitedDropMetaCache).evict(1L);
			verify(limitedDropRedisService, never()).reserve(any(), any());
			verify(limitedDropRedisService).recordAttempt(1L, LimitedAttemptResult.NOT_OPEN);
		}

		@Test
		@DisplayName("메타가 마감이면 캐시를 다시 읽어도 거절돼 Redis 를 호출하지 않고 LIMITED_CLOSED 예외를 던진다")
		void throwsWhenClosed() {
			// given
			LimitedDropMeta meta = closedMeta(2L);
			given(limitedDropMetaCache.get(2L)).willReturn(Optional.of(meta));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(2L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_CLOSED);
			verify(limitedDropRedisService, never()).reserve(any(), any());
			verify(limitedDropRedisService, never()).recordAttempt(any(), any());
		}

		@Test
		@DisplayName("메타는 stale 로 거절했지만 캐시를 지운 뒤 다시 읽은 값이 OPEN 이면 통과해 정상 처리한다")
		void retriesAndSucceedsWhenRefreshedMetaIsOpen() {
			// given
			LimitedDropMeta stale = scheduledMeta(1L);
			LimitedDropMeta refreshed = openMeta(1L);
			given(limitedDropMetaCache.get(1L)).willReturn(Optional.of(stale), Optional.of(refreshed));
			given(limitedDropRedisService.reserve(1L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.OK);
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(1L, 10L, 20L, PRODUCT_ID)).willReturn(response);

			// when
			LimitedPurchaseResponse result = limitedPurchaseService.purchase(1L, 10L, 20L);

			// then
			assertThat(result).isEqualTo(response);
			verify(limitedDropMetaCache).evict(1L);
			verify(limitedDropRedisService).reserve(1L, 10L);
		}

		@Test
		@DisplayName("OPEN 상태에서 Redis 가 SOLD_OUT 을 주는 탈락자는 메타 캐시를 다시 읽지 않는다")
		void doesNotReloadMetaWhenRedisRejectsWithinOpenWindow() {
			// given
			LimitedDropMeta meta = openMeta(4L);
			given(limitedDropMetaCache.get(4L)).willReturn(Optional.of(meta));
			given(limitedDropRedisService.reserve(4L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.SOLD_OUT);

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(4L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_SOLD_OUT);
			verify(limitedDropMetaCache, times(1)).get(4L);
			verify(limitedDropMetaCache, never()).evict(any());
			verify(limitedDropRedisService).recordAttempt(4L, LimitedAttemptResult.SOLD_OUT);
		}

		@Test
		@DisplayName("Redis 선점이 ALREADY 면 LIMITED_ALREADY_PURCHASED 예외를 던지고 Writer 를 호출하지 않는다")
		void throwsWhenAlreadyPurchased() {
			// given
			LimitedDropMeta meta = openMeta(3L);
			given(limitedDropMetaCache.get(3L)).willReturn(Optional.of(meta));
			given(limitedDropRedisService.reserve(3L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.ALREADY);

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(3L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_ALREADY_PURCHASED);
			verify(limitedPurchaseWriter, never()).write(any(), any(), any(), any());
			verify(limitedDropRedisService).recordAttempt(3L, LimitedAttemptResult.ALREADY_PURCHASED);
		}

		@Test
		@DisplayName("Redis 재고 키가 유실됐고 재적재에 성공하면 재시도해 정상 처리한다")
		void retriesReserveWhenRebuildSucceedsAfterNotInitialized() {
			// given
			LimitedDropMeta meta = openMeta(10L);
			given(limitedDropMetaCache.get(10L)).willReturn(Optional.of(meta));
			given(limitedDropRedisService.reserve(10L, 10L))
					.willReturn(LimitedDropRedisService.ReserveResult.NOT_INITIALIZED,
							LimitedDropRedisService.ReserveResult.OK);
			given(limitedDropSyncService.rebuildOnce(10L)).willReturn(true);
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(10L, 10L, 20L, PRODUCT_ID)).willReturn(response);

			// when
			LimitedPurchaseResponse result = limitedPurchaseService.purchase(10L, 10L, 20L);

			// then
			assertThat(result).isEqualTo(response);
			verify(limitedDropRedisService, times(2)).reserve(10L, 10L);
		}

		@Test
		@DisplayName("Redis 재고 키가 유실됐고 재적재 락을 못 잡으면 LIMITED_NOT_OPEN 예외를 던진다")
		void throwsNotOpenWhenRebuildFailsAfterNotInitialized() {
			// given
			LimitedDropMeta meta = openMeta(11L);
			given(limitedDropMetaCache.get(11L)).willReturn(Optional.of(meta));
			given(limitedDropRedisService.reserve(11L, 10L))
					.willReturn(LimitedDropRedisService.ReserveResult.NOT_INITIALIZED);
			given(limitedDropSyncService.rebuildOnce(11L)).willReturn(false);

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(11L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_NOT_OPEN);
			verify(limitedDropRedisService, times(1)).reserve(11L, 10L);
			verify(limitedPurchaseWriter, never()).write(any(), any(), any(), any());
		}

		@Test
		@DisplayName("Writer 에서 예외가 나면 Redis 선점을 되돌리고 예외를 그대로 던진다")
		void releasesReservationWhenWriterFails() {
			// given
			LimitedDropMeta meta = openMeta(5L);
			given(limitedDropMetaCache.get(5L)).willReturn(Optional.of(meta));
			given(limitedDropRedisService.reserve(5L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.OK);
			given(limitedPurchaseWriter.write(5L, 10L, 20L, PRODUCT_ID))
					.willThrow(new BusinessException(ErrorCode.MEMBER_ADDRESS_NOT_FOUND));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(5L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_ADDRESS_NOT_FOUND);
			verify(limitedDropRedisService).release(5L, 10L);
		}

		@Test
		@DisplayName("Writer 에서 BusinessException 이 아닌 런타임 예외가 나도 Redis 선점을 되돌리고 예외를 그대로 던진다")
		void releasesReservationWhenWriterThrowsNonBusinessException() {
			// given
			LimitedDropMeta meta = openMeta(9L);
			given(limitedDropMetaCache.get(9L)).willReturn(Optional.of(meta));
			given(limitedDropRedisService.reserve(9L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.OK);
			given(limitedPurchaseWriter.write(9L, 10L, 20L, PRODUCT_ID))
					.willThrow(new IllegalStateException("DB 커넥션 끊김"));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(9L, 10L, 20L))
					.isInstanceOf(IllegalStateException.class);
			verify(limitedDropRedisService).release(9L, 10L);
		}

		@Test
		@DisplayName("정상 흐름이면 Writer 결과를 그대로 반환한다")
		void returnsWriterResultOnSuccess() {
			// given
			LimitedDropMeta meta = openMeta(6L);
			given(limitedDropMetaCache.get(6L)).willReturn(Optional.of(meta));
			given(limitedDropRedisService.reserve(6L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.OK);
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(6L, 10L, 20L, PRODUCT_ID)).willReturn(response);

			// when
			LimitedPurchaseResponse result = limitedPurchaseService.purchase(6L, 10L, 20L);

			// then
			assertThat(result).isEqualTo(response);
			verify(limitedDropRedisService, never()).release(any(), any());
			verify(limitedDropRedisService, never()).recordAttempt(any(), any());
			verify(limitedRedisCircuitBreaker).onSuccess();
		}
	}

	@Nested
	@DisplayName("purchase() - limited.redis-enabled=false")
	class PurchaseWithRedisDisabled {

		@Test
		@DisplayName("Redis 를 건너뛰고 Writer 결과를 그대로 반환한다")
		void writesWithoutTouchingRedis() {
			// given
			LimitedProperties redisDisabled = new LimitedProperties(false, Duration.ofSeconds(3));
			LimitedPurchaseService service = new LimitedPurchaseService(limitedDropMetaCache, limitedDropRedisService,
					limitedPurchaseWriter, limitedDropSyncService, limitedRedisCircuitBreaker, limitedFallbackGate,
					redisDisabled, limitedCircuitProperties, clock);
			LimitedDropMeta meta = openMeta(7L);
			given(limitedDropMetaCache.get(7L)).willReturn(Optional.of(meta));
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(7L, 10L, 20L, PRODUCT_ID)).willReturn(response);

			// when
			LimitedPurchaseResponse result = service.purchase(7L, 10L, 20L);

			// then
			assertThat(result).isEqualTo(response);
			verifyNoInteractions(limitedDropRedisService);
			verifyNoInteractions(limitedRedisCircuitBreaker);
		}

		@Test
		@DisplayName("Writer 에서 예외가 나면 Redis 를 호출하지 않고 예외를 그대로 던진다")
		void propagatesWriterExceptionWithoutRelease() {
			// given
			LimitedProperties redisDisabled = new LimitedProperties(false, Duration.ofSeconds(3));
			LimitedPurchaseService service = new LimitedPurchaseService(limitedDropMetaCache, limitedDropRedisService,
					limitedPurchaseWriter, limitedDropSyncService, limitedRedisCircuitBreaker, limitedFallbackGate,
					redisDisabled, limitedCircuitProperties, clock);
			LimitedDropMeta meta = openMeta(8L);
			given(limitedDropMetaCache.get(8L)).willReturn(Optional.of(meta));
			given(limitedPurchaseWriter.write(8L, 10L, 20L, PRODUCT_ID))
					.willThrow(new BusinessException(ErrorCode.MEMBER_ADDRESS_NOT_FOUND));

			// when & then
			assertThatThrownBy(() -> service.purchase(8L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_ADDRESS_NOT_FOUND);
			verifyNoInteractions(limitedDropRedisService);
			verifyNoInteractions(limitedRedisCircuitBreaker);
		}
	}

	@Nested
	@DisplayName("purchase() - Redis 서킷·DB 폴백")
	class PurchaseWithRedisFallback {

		@Test
		@DisplayName("reserve 가 Redis 장애로 실패하면 서킷에 실패를 기록하고 DB 경로로 폴백해 응답을 그대로 반환한다")
		void fallsBackToDbWhenReserveFailsWithDataAccessException() {
			// given
			LimitedDropMeta meta = openMeta(20L);
			given(limitedDropMetaCache.get(20L)).willReturn(Optional.of(meta));
			given(limitedDropRedisService.reserve(20L, 10L))
					.willThrow(new RedisConnectionFailureException("connection refused"));
			given(limitedFallbackGate.tryEnter(20L)).willReturn(true);
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(20L, 10L, 20L, PRODUCT_ID)).willReturn(response);

			// when
			LimitedPurchaseResponse result = limitedPurchaseService.purchase(20L, 10L, 20L);

			// then
			assertThat(result).isEqualTo(response);
			verify(limitedRedisCircuitBreaker).onFailure();
			verify(limitedRedisCircuitBreaker).noteFallback(20L);
			verify(limitedFallbackGate).tryEnter(20L);
			verify(limitedFallbackGate).exit(20L);
			verify(limitedDropRedisService, never()).release(any(), any());
		}

		@Test
		@DisplayName("서킷이 Redis 를 막고 있으면 reserve 를 호출하지 않고 바로 DB 경로로 처리한다")
		void skipsReserveWhenCircuitDisallowsRedis() {
			// given
			given(limitedRedisCircuitBreaker.allowRedis()).willReturn(false);
			given(limitedFallbackGate.tryEnter(21L)).willReturn(true);
			LimitedDropMeta meta = openMeta(21L);
			given(limitedDropMetaCache.get(21L)).willReturn(Optional.of(meta));
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(21L, 10L, 20L, PRODUCT_ID)).willReturn(response);

			// when
			LimitedPurchaseResponse result = limitedPurchaseService.purchase(21L, 10L, 20L);

			// then
			assertThat(result).isEqualTo(response);
			verify(limitedDropRedisService, never()).reserve(any(), any());
			verify(limitedRedisCircuitBreaker).noteFallback(21L);
		}

		@Test
		@DisplayName("폴백 게이트가 거절하면 LIMITED_BUSY 예외를 던진다")
		void throwsBusyWhenFallbackGateRejects() {
			// given
			given(limitedRedisCircuitBreaker.allowRedis()).willReturn(false);
			given(limitedFallbackGate.tryEnter(22L)).willReturn(false);
			LimitedDropMeta meta = openMeta(22L);
			given(limitedDropMetaCache.get(22L)).willReturn(Optional.of(meta));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(22L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_BUSY);
			verify(limitedPurchaseWriter, never()).write(any(), any(), any(), any());
			verify(limitedRedisCircuitBreaker, never()).noteFallback(any());
		}

		@Test
		@DisplayName("fallback-enabled 가 false 면 게이트를 거치지 않고 곧바로 LIMITED_BUSY 예외를 던진다")
		void throwsBusyImmediatelyWhenFallbackDisabled() {
			// given
			LimitedCircuitProperties fallbackDisabled = new LimitedCircuitProperties(5, Duration.ofSeconds(10), 5,
					false);
			LimitedPurchaseService service = new LimitedPurchaseService(limitedDropMetaCache, limitedDropRedisService,
					limitedPurchaseWriter, limitedDropSyncService, limitedRedisCircuitBreaker, limitedFallbackGate,
					new LimitedProperties(true, Duration.ofSeconds(3)), fallbackDisabled, clock);
			given(limitedRedisCircuitBreaker.allowRedis()).willReturn(false);
			LimitedDropMeta meta = openMeta(23L);
			given(limitedDropMetaCache.get(23L)).willReturn(Optional.of(meta));

			// when & then
			assertThatThrownBy(() -> service.purchase(23L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_BUSY);
			verifyNoInteractions(limitedFallbackGate);
		}

		@Test
		@DisplayName("서킷이 CLOSED 가 아니면 폴백 경로에서 예외가 나도 시도 집계를 기록하지 않는다")
		void skipsRecordAttemptWhenCircuitNotClosed() {
			// given
			given(limitedRedisCircuitBreaker.allowRedis()).willReturn(false);
			given(limitedRedisCircuitBreaker.isClosed()).willReturn(false);
			given(limitedFallbackGate.tryEnter(24L)).willReturn(true);
			LimitedDropMeta meta = openMeta(24L);
			given(limitedDropMetaCache.get(24L)).willReturn(Optional.of(meta));
			given(limitedPurchaseWriter.write(24L, 10L, 20L, PRODUCT_ID))
					.willThrow(new BusinessException(ErrorCode.LIMITED_SOLD_OUT));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(24L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_SOLD_OUT);
			verify(limitedDropRedisService, never()).recordAttempt(any(), any());
		}

		@Test
		@DisplayName("HALF_OPEN 프로브가 reserve 에 성공하면 폴백 중 쌓인 드롭을 재적재하고 목록을 비운다")
		void resyncsFallbackDropsOnSuccessfulProbe() {
			// given
			LimitedDropMeta meta = openMeta(25L);
			given(limitedDropMetaCache.get(25L)).willReturn(Optional.of(meta));
			given(limitedRedisCircuitBreaker.fallbackDrops()).willReturn(Set.of(25L, 30L));
			given(limitedDropRedisService.reserve(25L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.OK);
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(25L, 10L, 20L, PRODUCT_ID)).willReturn(response);

			// when
			LimitedPurchaseResponse result = limitedPurchaseService.purchase(25L, 10L, 20L);

			// then
			assertThat(result).isEqualTo(response);
			verify(limitedDropSyncService).sync(25L);
			verify(limitedDropSyncService).sync(30L);
			verify(limitedRedisCircuitBreaker).clearFallbackDrops(Set.of(25L, 30L));
			verify(limitedRedisCircuitBreaker).onSuccess();
		}
	}

	private LimitedDropMeta scheduledMeta(Long dropId) {
		LocalDateTime nowValue = LocalDateTime.now(clock);
		return new LimitedDropMeta(dropId, LimitedDropStatus.SCHEDULED, nowValue.plusHours(1), nowValue.plusHours(2),
				PRODUCT_ID);
	}

	private LimitedDropMeta closedMeta(Long dropId) {
		LocalDateTime nowValue = LocalDateTime.now(clock);
		return new LimitedDropMeta(dropId, LimitedDropStatus.CLOSED, nowValue.minusHours(2), nowValue.minusHours(1),
				PRODUCT_ID);
	}

	private LimitedDropMeta openMeta(Long dropId) {
		LocalDateTime nowValue = LocalDateTime.now(clock);
		return new LimitedDropMeta(dropId, LimitedDropStatus.OPEN, nowValue.minusHours(1), nowValue.plusHours(1),
				PRODUCT_ID);
	}
}
