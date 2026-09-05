package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
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

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.config.LimitedProperties;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
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

	private Clock clock;

	private LimitedPurchaseService limitedPurchaseService;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		limitedPurchaseService = new LimitedPurchaseService(limitedDropRepository, limitedDropRedisService,
				limitedPurchaseWriter, new LimitedProperties(true), clock);
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
		}

		@Test
		@DisplayName("마감된 한정반이면 Redis 를 호출하지 않고 LIMITED_CLOSED 예외를 던진다")
		void throwsWhenClosed() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.open(product(), 10), 2L);
			LimitedDropFixture.withStatus(drop,
					com.groove.limited.entity.LimitedDropStatus.CLOSED);
			given(limitedDropRepository.findById(2L)).willReturn(Optional.of(drop));

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(2L, 10L, 20L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_CLOSED);
			verify(limitedDropRedisService, never()).reserve(any(), any());
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
					limitedDropRedisService, limitedPurchaseWriter, redisDisabled, clock);
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
		}

		@Test
		@DisplayName("Writer 에서 예외가 나면 Redis 를 호출하지 않고 예외를 그대로 던진다")
		void propagatesWriterExceptionWithoutRelease() {
			// given
			LimitedProperties redisDisabled = new LimitedProperties(false);
			LimitedPurchaseService service = new LimitedPurchaseService(limitedDropRepository,
					limitedDropRedisService, limitedPurchaseWriter, redisDisabled, clock);
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
