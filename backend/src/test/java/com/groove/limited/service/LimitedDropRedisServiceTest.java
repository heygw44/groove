package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

	private static Long newDropId() {
		return ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
	}
}
