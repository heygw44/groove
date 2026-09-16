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

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.config.LimitedCircuitProperties;
import com.groove.limited.config.LimitedProperties;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedAttemptResult;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class LimitedPurchaseServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private LimitedDropRepository limitedDropRepository;

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
		limitedPurchaseService = new LimitedPurchaseService(limitedDropRepository, limitedDropRedisService,
				limitedPurchaseWriter, limitedDropSyncService, limitedRedisCircuitBreaker, limitedFallbackGate,
				new LimitedProperties(true), limitedCircuitProperties, clock);
	}

	@Nested
	@DisplayName("purchase()")
	class Purchase {

		@Test
		@DisplayName("아직 오픈 전이면 Redis 를 호출하지 않고 LIMITED_NOT_OPEN 예외를 던진다")
		void throwsWhenNotOpenYet() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product()), 1L);
			given(limitedDropRepository.findById(1L)).willReturn(Optional.of(drop));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(1L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_NOT_OPEN);
			verify(limitedDropRedisService, never()).reserve(any(), any());
			verify(limitedDropRedisService).recordAttempt(1L, LimitedAttemptResult.NOT_OPEN);
		}

		@Test
		@DisplayName("마감된 한정반이면 Redis 를 호출하지 않고 LIMITED_CLOSED 예외를 던진다")
		void throwsWhenClosed() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.open(product(), 10), 2L);
			LimitedDropFixture.withStatus(drop, LimitedDropStatus.CLOSED);
			given(limitedDropRepository.findById(2L)).willReturn(Optional.of(drop));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(2L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_CLOSED);
			verify(limitedDropRedisService, never()).reserve(any(), any());
			verify(limitedDropRedisService, never()).recordAttempt(any(), any());
		}

		@Test
		@DisplayName("Redis 선점이 ALREADY 면 LIMITED_ALREADY_PURCHASED 예외를 던지고 Writer 를 호출하지 않는다")
		void throwsWhenAlreadyPurchased() {
			// given
			LimitedDrop drop = openDrop(3L);
			given(limitedDropRepository.findById(3L)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.reserve(3L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.ALREADY);

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(3L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_ALREADY_PURCHASED);
			verify(limitedPurchaseWriter, never()).write(any(), any(), any());
			verify(limitedDropRedisService).recordAttempt(3L, LimitedAttemptResult.ALREADY_PURCHASED);
		}

		@Test
		@DisplayName("Redis 선점이 SOLD_OUT 이면 LIMITED_SOLD_OUT 예외를 던진다")
		void throwsWhenSoldOutInRedis() {
			// given
			LimitedDrop drop = openDrop(4L);
			given(limitedDropRepository.findById(4L)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.reserve(4L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.SOLD_OUT);

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(4L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_SOLD_OUT);
			verify(limitedDropRedisService).recordAttempt(4L, LimitedAttemptResult.SOLD_OUT);
		}

		@Test
		@DisplayName("Redis 재고 키가 유실됐고 재적재에 성공하면 재시도해 정상 처리한다")
		void retriesReserveWhenRebuildSucceedsAfterNotInitialized() {
			// given
			LimitedDrop drop = openDrop(10L);
			given(limitedDropRepository.findById(10L)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.reserve(10L, 10L))
					.willReturn(LimitedDropRedisService.ReserveResult.NOT_INITIALIZED,
							LimitedDropRedisService.ReserveResult.OK);
			given(limitedDropSyncService.rebuildOnce(10L)).willReturn(true);
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(10L, 10L, 20L)).willReturn(response);

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
			LimitedDrop drop = openDrop(11L);
			given(limitedDropRepository.findById(11L)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.reserve(11L, 10L))
					.willReturn(LimitedDropRedisService.ReserveResult.NOT_INITIALIZED);
			given(limitedDropSyncService.rebuildOnce(11L)).willReturn(false);

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(11L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_NOT_OPEN);
			verify(limitedDropRedisService, times(1)).reserve(11L, 10L);
			verify(limitedPurchaseWriter, never()).write(any(), any(), any());
		}

		@Test
		@DisplayName("Writer 에서 예외가 나면 Redis 선점을 되돌리고 예외를 그대로 던진다")
		void releasesReservationWhenWriterFails() {
			// given
			LimitedDrop drop = openDrop(5L);
			given(limitedDropRepository.findById(5L)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.reserve(5L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.OK);
			given(limitedPurchaseWriter.write(5L, 10L, 20L))
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
			LimitedDrop drop = openDrop(9L);
			given(limitedDropRepository.findById(9L)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.reserve(9L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.OK);
			given(limitedPurchaseWriter.write(9L, 10L, 20L))
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
			LimitedDrop drop = openDrop(6L);
			given(limitedDropRepository.findById(6L)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.reserve(6L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.OK);
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(6L, 10L, 20L)).willReturn(response);

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
			LimitedProperties redisDisabled = new LimitedProperties(false);
			LimitedPurchaseService service = new LimitedPurchaseService(limitedDropRepository,
					limitedDropRedisService, limitedPurchaseWriter, limitedDropSyncService,
					limitedRedisCircuitBreaker, limitedFallbackGate, redisDisabled, limitedCircuitProperties, clock);
			LimitedDrop drop = openDrop(7L);
			given(limitedDropRepository.findById(7L)).willReturn(Optional.of(drop));
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(7L, 10L, 20L)).willReturn(response);

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
			LimitedProperties redisDisabled = new LimitedProperties(false);
			LimitedPurchaseService service = new LimitedPurchaseService(limitedDropRepository,
					limitedDropRedisService, limitedPurchaseWriter, limitedDropSyncService,
					limitedRedisCircuitBreaker, limitedFallbackGate, redisDisabled, limitedCircuitProperties, clock);
			LimitedDrop drop = openDrop(8L);
			given(limitedDropRepository.findById(8L)).willReturn(Optional.of(drop));
			given(limitedPurchaseWriter.write(8L, 10L, 20L))
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
			LimitedDrop drop = openDrop(20L);
			given(limitedDropRepository.findById(20L)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.reserve(20L, 10L))
					.willThrow(new RedisConnectionFailureException("connection refused"));
			given(limitedFallbackGate.tryEnter(20L)).willReturn(true);
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(20L, 10L, 20L)).willReturn(response);

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
			LimitedDrop drop = openDrop(21L);
			given(limitedDropRepository.findById(21L)).willReturn(Optional.of(drop));
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(21L, 10L, 20L)).willReturn(response);

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
			LimitedDrop drop = openDrop(22L);
			given(limitedDropRepository.findById(22L)).willReturn(Optional.of(drop));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(22L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_BUSY);
			verify(limitedPurchaseWriter, never()).write(any(), any(), any());
			verify(limitedRedisCircuitBreaker, never()).noteFallback(any());
		}

		@Test
		@DisplayName("fallback-enabled 가 false 면 게이트를 거치지 않고 곧바로 LIMITED_BUSY 예외를 던진다")
		void throwsBusyImmediatelyWhenFallbackDisabled() {
			// given
			LimitedCircuitProperties fallbackDisabled = new LimitedCircuitProperties(5, Duration.ofSeconds(10), 5,
					false);
			LimitedPurchaseService service = new LimitedPurchaseService(limitedDropRepository,
					limitedDropRedisService, limitedPurchaseWriter, limitedDropSyncService,
					limitedRedisCircuitBreaker, limitedFallbackGate, new LimitedProperties(true), fallbackDisabled,
					clock);
			given(limitedRedisCircuitBreaker.allowRedis()).willReturn(false);
			LimitedDrop drop = openDrop(23L);
			given(limitedDropRepository.findById(23L)).willReturn(Optional.of(drop));

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
			LimitedDrop drop = openDrop(24L);
			given(limitedDropRepository.findById(24L)).willReturn(Optional.of(drop));
			given(limitedPurchaseWriter.write(24L, 10L, 20L))
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
			LimitedDrop drop = openDrop(25L);
			given(limitedDropRepository.findById(25L)).willReturn(Optional.of(drop));
			given(limitedRedisCircuitBreaker.fallbackDrops()).willReturn(Set.of(25L, 30L));
			given(limitedDropRedisService.reserve(25L, 10L)).willReturn(LimitedDropRedisService.ReserveResult.OK);
			LimitedPurchaseResponse response = new LimitedPurchaseResponse(1L, "20260904-ABCDE123",
					new BigDecimal("10000"), LocalDateTime.now(clock));
			given(limitedPurchaseWriter.write(25L, 10L, 20L)).willReturn(response);

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

	private LimitedDrop openDrop(Long id) {
		LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.open(product(), 10), id);
		LocalDateTime now = LocalDateTime.now(clock);
		LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
		LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
		return drop;
	}

	private static Product product() {
		Artist artist = ArtistFixture.withId(1L);
		return ProductFixture.withId(ProductFixture.create(artist), 100L);
	}
}
