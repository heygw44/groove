package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductViewLogWriterTest {

	@Mock
	ProductViewLogSaver productViewLogSaver;

	@Mock
	RecentViewRedisService recentViewRedisService;

	@InjectMocks
	ProductViewLogWriter productViewLogWriter;

	@Nested
	@DisplayName("handle()")
	class Handle {

		@Test
		@DisplayName("DB 저장이 실패해도 예외를 전파하지 않고 Redis 적재는 그대로 시도한다")
		void doesNotThrowWhenDbSaveFails() {
			// given
			ProductViewedEvent event = new ProductViewedEvent(1L, 10L, LocalDateTime.now());
			willThrow(new RuntimeException("boom")).given(productViewLogSaver).save(event);

			// when & then
			assertThatCode(() -> productViewLogWriter.handle(event)).doesNotThrowAnyException();
			verify(recentViewRedisService).push(1L, 10L);
		}

		@Test
		@DisplayName("Redis 적재가 실패해도 예외를 전파하지 않고 DB 저장은 그대로 수행된다")
		void doesNotThrowWhenRedisPushFails() {
			// given
			ProductViewedEvent event = new ProductViewedEvent(1L, 10L, LocalDateTime.now());
			willThrow(new RuntimeException("boom")).given(recentViewRedisService).push(1L, 10L);

			// when & then
			assertThatCode(() -> productViewLogWriter.handle(event)).doesNotThrowAnyException();
			verify(productViewLogSaver).save(event);
		}

		@Test
		@DisplayName("memberId 가 없으면 Redis 적재를 건너뛴다")
		void skipsRedisPushWhenMemberIdIsNull() {
			// given
			ProductViewedEvent event = new ProductViewedEvent(null, 10L, LocalDateTime.now());

			// when
			productViewLogWriter.handle(event);

			// then
			verify(productViewLogSaver).save(event);
			verify(recentViewRedisService, never()).push(any(), any());
		}
	}
}
