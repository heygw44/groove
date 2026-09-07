package com.groove.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

	private static final Long PRODUCT_ID = 100L;
	private static final Long ALBUM_ID = 200L;
	private static final String PRODUCT_TITLE = "Kind of Blue";
	private static final String ALBUM_TITLE = "A Love Supreme";

	@Mock
	NotificationDispatcher notificationDispatcher;

	@InjectMocks
	NotificationEventListener notificationEventListener;

	@Nested
	@DisplayName("handleRestock()")
	class HandleRestock {

		@Test
		@DisplayName("적재가 실패해도 예외를 전파하지 않는다")
		void doesNotThrowWhenDispatchFails() {
			// given
			RestockEvent event = new RestockEvent(PRODUCT_ID, PRODUCT_TITLE);
			willThrow(new RuntimeException("boom")).given(notificationDispatcher).dispatchRestock(event);

			// when & then
			assertThatCode(() -> notificationEventListener.handleRestock(event)).doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("handlePriceDrop()")
	class HandlePriceDrop {

		@Test
		@DisplayName("적재가 실패해도 예외를 전파하지 않는다")
		void doesNotThrowWhenDispatchFails() {
			// given
			PriceDropEvent event = new PriceDropEvent(PRODUCT_ID, PRODUCT_TITLE);
			willThrow(new RuntimeException("boom")).given(notificationDispatcher).dispatchPriceDrop(event);

			// when & then
			assertThatCode(() -> notificationEventListener.handlePriceDrop(event)).doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("handleNewPressing()")
	class HandleNewPressing {

		@Test
		@DisplayName("적재가 실패해도 예외를 전파하지 않는다")
		void doesNotThrowWhenDispatchFails() {
			// given
			NewPressingEvent event = new NewPressingEvent(ALBUM_ID, ALBUM_TITLE);
			willThrow(new RuntimeException("boom")).given(notificationDispatcher).dispatchNewPressing(event);

			// when & then
			assertThatCode(() -> notificationEventListener.handleNewPressing(event)).doesNotThrowAnyException();
		}
	}
}
