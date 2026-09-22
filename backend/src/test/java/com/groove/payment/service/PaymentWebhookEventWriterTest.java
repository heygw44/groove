package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.payment.entity.PaymentWebhookEvent;
import com.groove.payment.entity.PaymentWebhookResult;
import com.groove.payment.repository.PaymentWebhookEventRepository;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookEventWriterTest {

	private static final String PAYMENT_KEY = "webhook-key";
	private static final String TOSS_STATUS = "DONE";
	private static final Long EVENT_ID = 7L;

	@Mock
	private PaymentWebhookEventRepository repository;

	private PaymentWebhookEventWriter writer;

	private Clock clock;
	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-22T05:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		writer = new PaymentWebhookEventWriter(repository, clock);
	}

	@Nested
	@DisplayName("receive()")
	class Receive {

		@Test
		@DisplayName("같은 키의 행이 없으면 새로 저장한다")
		void savesNewEventWhenNoneExists() {
			// given
			OffsetDateTime eventCreatedAt = OffsetDateTime.parse("2026-09-22T14:00:00+09:00");
			given(repository.findByPaymentKeyAndTossStatusAndEventCreatedAt(eq(PAYMENT_KEY), eq(TOSS_STATUS), any()))
					.willReturn(Optional.empty());
			PaymentWebhookEvent saved = eventWithId();
			given(repository.save(any())).willReturn(saved);

			// when
			Optional<PaymentWebhookEvent> result = writer.receive("PAYMENT_STATUS_CHANGED", PAYMENT_KEY,
					"20260922-ABCDEFGH", TOSS_STATUS, eventCreatedAt);

			// then
			assertThat(result).contains(saved);
			ArgumentCaptor<PaymentWebhookEvent> captor = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
			verify(repository).save(captor.capture());
			assertThat(captor.getValue().getEventCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 22, 14, 0));
			assertThat(captor.getValue().getReceivedAt()).isEqualTo(now);
		}

		@Test
		@DisplayName("createdAt 이 없으면 eventCreatedAt 을 null 로 저장한다")
		void storesNullEventCreatedAtWhenMissing() {
			// given
			given(repository.findByPaymentKeyAndTossStatusAndEventCreatedAt(PAYMENT_KEY, TOSS_STATUS, null))
					.willReturn(Optional.empty());
			given(repository.save(any())).willReturn(eventWithId());

			// when
			writer.receive("PAYMENT_STATUS_CHANGED", PAYMENT_KEY, "20260922-ABCDEFGH", TOSS_STATUS, null);

			// then
			ArgumentCaptor<PaymentWebhookEvent> captor = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
			verify(repository).save(captor.capture());
			assertThat(captor.getValue().getEventCreatedAt()).isNull();
		}

		@Test
		@DisplayName("같은 키의 행이 있고 ERROR 가 아니면 다시 처리하지 않도록 빈 값을 돌려준다")
		void skipsWhenExistingEventIsNotError() {
			// given
			PaymentWebhookEvent existing = eventWithId();
			existing.markProcessed(PaymentWebhookResult.APPLIED, "webhook", now);
			given(repository.findByPaymentKeyAndTossStatusAndEventCreatedAt(eq(PAYMENT_KEY), eq(TOSS_STATUS), any()))
					.willReturn(Optional.of(existing));

			// when
			Optional<PaymentWebhookEvent> result = writer.receive("PAYMENT_STATUS_CHANGED", PAYMENT_KEY,
					"20260922-ABCDEFGH", TOSS_STATUS, OffsetDateTime.parse("2026-09-22T14:00:00+09:00"));

			// then
			assertThat(result).isEmpty();
			verify(repository, never()).save(any());
		}

		@Test
		@DisplayName("같은 키의 행이 ERROR 면 새로 만들지 않고 그 행을 재사용한다")
		void reusesExistingEventWhenErrored() {
			// given
			PaymentWebhookEvent existing = eventWithId();
			existing.markProcessed(PaymentWebhookResult.ERROR, "재조회 실패", now);
			given(repository.findByPaymentKeyAndTossStatusAndEventCreatedAt(eq(PAYMENT_KEY), eq(TOSS_STATUS), any()))
					.willReturn(Optional.of(existing));

			// when
			Optional<PaymentWebhookEvent> result = writer.receive("PAYMENT_STATUS_CHANGED", PAYMENT_KEY,
					"20260922-ABCDEFGH", TOSS_STATUS, OffsetDateTime.parse("2026-09-22T14:00:00+09:00"));

			// then
			assertThat(result).contains(existing);
			verify(repository, never()).save(any());
		}
	}

	@Nested
	@DisplayName("markResult()")
	class MarkResult {

		@Test
		@DisplayName("행이 있으면 결과·상세·처리 시각을 반영한다")
		void marksProcessedWhenEventExists() {
			// given
			PaymentWebhookEvent event = eventWithId();
			given(repository.findById(EVENT_ID)).willReturn(Optional.of(event));

			// when
			writer.markResult(EVENT_ID, PaymentWebhookResult.APPLIED, "webhook");

			// then
			assertThat(event.getResult()).isEqualTo(PaymentWebhookResult.APPLIED);
			assertThat(event.getDetail()).isEqualTo("webhook");
			assertThat(event.getProcessedAt()).isEqualTo(now);
		}

		@Test
		@DisplayName("행이 없으면 예외를 던진다")
		void throwsWhenEventNotFound() {
			// given
			given(repository.findById(EVENT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.markResult(EVENT_ID, PaymentWebhookResult.ERROR, "재조회 실패"))
					.isInstanceOf(IllegalStateException.class);
		}
	}

	private PaymentWebhookEvent eventWithId() {
		PaymentWebhookEvent event = PaymentWebhookEvent.receive("PAYMENT_STATUS_CHANGED", PAYMENT_KEY,
				"20260922-ABCDEFGH", TOSS_STATUS, LocalDateTime.of(2026, 9, 22, 14, 0), now);
		ReflectionTestUtils.setField(event, "id", EVENT_ID);
		return event;
	}
}
