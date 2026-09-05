package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.support.IntegrationTestSupport;

class LimitedDropRedisServiceTest extends IntegrationTestSupport {

	@Autowired
	LimitedDropRedisService limitedDropRedisService;

	@Autowired
	StringRedisTemplate redisTemplate;

	private Long dropId;

	@AfterEach
	void tearDown() {
		limitedDropRedisService.clear(dropId);
	}

	@Nested
	@DisplayName("initStock()")
	class InitStock {

		@Test
		@DisplayName("처음 호출하면 재고 키를 세팅하고 true 를 반환한다")
		void setsStockKeyOnFirstCall() {
			// given
			dropId = newDropId();

			// when
			boolean result = limitedDropRedisService.initStock(dropId, 100);

			// then
			assertThat(result).isTrue();
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId))).isEqualTo("100");
		}

		@Test
		@DisplayName("이미 키가 있으면 값을 덮어쓰지 않고 false 를 반환한다")
		void doesNotOverwriteExistingStockKey() {
			// given
			dropId = newDropId();
			limitedDropRedisService.initStock(dropId, 100);

			// when
			boolean result = limitedDropRedisService.initStock(dropId, 50);

			// then
			assertThat(result).isFalse();
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId))).isEqualTo("100");
		}
	}

	@Nested
	@DisplayName("clear()")
	class Clear {

		@Test
		@DisplayName("재고 키와 구매자 키를 모두 지운다")
		void removesStockAndBuyersKeys() {
			// given
			dropId = newDropId();
			limitedDropRedisService.initStock(dropId, 100);
			redisTemplate.opsForSet().add(LimitedDropRedisService.buyersKey(dropId), "1");

			// when
			limitedDropRedisService.clear(dropId);

			// then
			assertThat(redisTemplate.hasKey(LimitedDropRedisService.stockKey(dropId))).isFalse();
			assertThat(redisTemplate.hasKey(LimitedDropRedisService.buyersKey(dropId))).isFalse();
		}

		@Test
		@DisplayName("키가 없는 드롭을 지워도 예외가 발생하지 않는다")
		void doesNotThrowWhenKeysAreAbsent() {
			// given
			dropId = newDropId();

			// when & then
			assertThatCode(() -> limitedDropRedisService.clear(dropId)).doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("getStock()")
	class GetStock {

		@Test
		@DisplayName("키가 있으면 값을 반환한다")
		void returnsValueWhenKeyExists() {
			// given
			dropId = newDropId();
			limitedDropRedisService.initStock(dropId, 30);

			// when
			Optional<Integer> result = limitedDropRedisService.getStock(dropId);

			// then
			assertThat(result).contains(30);
		}

		@Test
		@DisplayName("키가 없으면 empty 를 반환한다")
		void returnsEmptyWhenKeyMissing() {
			// given
			dropId = newDropId();

			// when
			Optional<Integer> result = limitedDropRedisService.getStock(dropId);

			// then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("getStocks()")
	class GetStocks {

		@Test
		@DisplayName("multiGet 으로 일부만 존재해도 있는 값만 채운다")
		void returnsOnlyExistingValues() {
			// given
			dropId = newDropId();
			Long missingDropId = newDropId();
			limitedDropRedisService.initStock(dropId, 42);

			try {
				// when
				Map<Long, Integer> result = limitedDropRedisService.getStocks(List.of(dropId, missingDropId));

				// then
				assertThat(result).containsExactly(Map.entry(dropId, 42));
			} finally {
				limitedDropRedisService.clear(missingDropId);
			}
		}
	}

	@Nested
	@DisplayName("reserve()")
	class Reserve {

		@Test
		@DisplayName("최초 요청이면 재고를 1 줄이고 OK 를 반환한다")
		void returnsOkOnFirstReserve() {
			// given
			dropId = newDropId();
			limitedDropRedisService.initStock(dropId, 10);

			// when
			LimitedDropRedisService.ReserveResult result = limitedDropRedisService.reserve(dropId, 1L);

			// then
			assertThat(result).isEqualTo(LimitedDropRedisService.ReserveResult.OK);
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId))).isEqualTo("9");
			assertThat(redisTemplate.opsForSet().isMember(LimitedDropRedisService.buyersKey(dropId), "1")).isTrue();
		}

		@Test
		@DisplayName("이미 구매한 회원이 다시 요청하면 재고를 건드리지 않고 ALREADY 를 반환한다")
		void returnsAlreadyOnRetryBySameMember() {
			// given
			dropId = newDropId();
			limitedDropRedisService.initStock(dropId, 10);
			limitedDropRedisService.reserve(dropId, 1L);

			// when
			LimitedDropRedisService.ReserveResult result = limitedDropRedisService.reserve(dropId, 1L);

			// then
			assertThat(result).isEqualTo(LimitedDropRedisService.ReserveResult.ALREADY);
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId))).isEqualTo("9");
		}

		@Test
		@DisplayName("재고가 0 이면 SOLD_OUT 을 반환한다")
		void returnsSoldOutWhenStockIsZero() {
			// given
			dropId = newDropId();
			limitedDropRedisService.initStock(dropId, 0);

			// when
			LimitedDropRedisService.ReserveResult result = limitedDropRedisService.reserve(dropId, 1L);

			// then
			assertThat(result).isEqualTo(LimitedDropRedisService.ReserveResult.SOLD_OUT);
		}

		@Test
		@DisplayName("재고 키가 세팅되지 않았으면 NOT_INITIALIZED 를 반환한다")
		void returnsNotInitializedWhenStockKeyMissing() {
			// given
			dropId = newDropId();

			// when
			LimitedDropRedisService.ReserveResult result = limitedDropRedisService.reserve(dropId, 1L);

			// then
			assertThat(result).isEqualTo(LimitedDropRedisService.ReserveResult.NOT_INITIALIZED);
		}
	}

	@Nested
	@DisplayName("release()")
	class Release {

		@Test
		@DisplayName("선점한 회원을 지우고 재고를 1 복구한다")
		void restoresStockAndRemovesBuyer() {
			// given
			dropId = newDropId();
			limitedDropRedisService.initStock(dropId, 10);
			limitedDropRedisService.reserve(dropId, 1L);

			// when
			limitedDropRedisService.release(dropId, 1L);

			// then
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId))).isEqualTo("10");
			assertThat(redisTemplate.opsForSet().isMember(LimitedDropRedisService.buyersKey(dropId), "1")).isFalse();
		}

		@Test
		@DisplayName("두 번 호출해도 재고가 중복 복구되지 않는다")
		void isIdempotentOnRepeatedCalls() {
			// given
			dropId = newDropId();
			limitedDropRedisService.initStock(dropId, 10);
			limitedDropRedisService.reserve(dropId, 1L);

			// when
			limitedDropRedisService.release(dropId, 1L);
			limitedDropRedisService.release(dropId, 1L);

			// then
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId))).isEqualTo("10");
		}
	}

	private static Long newDropId() {
		return ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
	}
}
