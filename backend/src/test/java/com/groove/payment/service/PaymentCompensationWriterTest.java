package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
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

import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.config.PaymentReconcileProperties;
import com.groove.payment.entity.PaymentCompensation;
import com.groove.payment.entity.PaymentCompensationStatus;
import com.groove.payment.repository.PaymentCompensationRepository;

@ExtendWith(MockitoExtension.class)
class PaymentCompensationWriterTest {

	private static final String PAYMENT_KEY = "tviva-dup-20260913abcdef";
	private static final Long ORDER_ID = 42L;
	private static final String TOSS_ORDER_ID = "20260913-K7Q2M9XZ";
	private static final String REASON = PaymentCompensator.DUPLICATE_APPROVAL_REASON;

	@Mock
	private PaymentCompensationRepository repository;

	@Mock
	private OrderRepository orderRepository;

	private PaymentCompensationWriter writer;

	private Order order;
	private LocalDateTime approvedAt;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-13T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		approvedAt = LocalDateTime.now(clock);
		Member member = MemberFixture.create();
		order = OrderFixture.create(member);
		PaymentReconcileProperties properties = new PaymentReconcileProperties(Duration.ofSeconds(60),
				Duration.ofMinutes(2), 50, 10);
		writer = new PaymentCompensationWriter(repository, orderRepository, properties);
	}

	@Nested
	@DisplayName("enqueue()")
	class Enqueue {

		@Test
		@DisplayName("같은 키가 없으면 대기 행을 새로 만든다")
		void savesNewRowWhenPaymentKeyIsNew() {
			// given
			given(repository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.empty());
			given(orderRepository.getReferenceById(ORDER_ID)).willReturn(order);

			// when
			writer.enqueue(PAYMENT_KEY, ORDER_ID, TOSS_ORDER_ID, approvedAt, REASON);

			// then
			verify(repository).save(any(PaymentCompensation.class));
		}

		@Test
		@DisplayName("같은 키가 이미 있으면 재사용하고 새로 만들지 않는다")
		void reusesExistingRowWhenPaymentKeyAlreadyQueued() {
			// given
			PaymentCompensation existing = PaymentCompensation.pending(PAYMENT_KEY, order, TOSS_ORDER_ID, approvedAt,
					REASON);
			given(repository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(existing));

			// when
			writer.enqueue(PAYMENT_KEY, ORDER_ID, TOSS_ORDER_ID, approvedAt, REASON);

			// then
			verify(orderRepository, never()).getReferenceById(any());
			verify(repository, never()).save(any());
		}
	}

	@Nested
	@DisplayName("complete()")
	class Complete {

		@Test
		@DisplayName("대기 행을 취소됨으로 남긴다")
		void marksCanceled() {
			// given
			PaymentCompensation compensation = PaymentCompensation.pending(PAYMENT_KEY, order, TOSS_ORDER_ID,
					approvedAt, REASON);
			given(repository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(compensation));

			// when
			writer.complete(PAYMENT_KEY, approvedAt);

			// then
			assertThat(compensation.getStatus()).isEqualTo(PaymentCompensationStatus.CANCELED);
			assertThat(compensation.getCanceledAt()).isEqualTo(approvedAt);
		}

		@Test
		@DisplayName("대기 행이 없으면 예외를 던진다")
		void throwsWhenRowMissing() {
			// given
			given(repository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.complete(PAYMENT_KEY, approvedAt))
					.isInstanceOf(IllegalStateException.class);
		}
	}

	@Nested
	@DisplayName("fail()")
	class Fail {

		@Test
		@DisplayName("상한 전이면 재시도 횟수만 올리고 대기 상태를 유지한다")
		void keepsPendingBeforeMaxAttempts() {
			// given
			PaymentCompensation compensation = PaymentCompensation.pending(PAYMENT_KEY, order, TOSS_ORDER_ID,
					approvedAt, REASON);
			given(repository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(compensation));

			// when
			writer.fail(PAYMENT_KEY, "TOSS 통신 실패");

			// then
			assertThat(compensation.getStatus()).isEqualTo(PaymentCompensationStatus.PENDING);
			assertThat(compensation.getAttempts()).isEqualTo(1);
		}

		@Test
		@DisplayName("상한에 닿으면 수동 확인으로 남긴다")
		void marksManualReviewAtMaxAttempts() {
			// given
			PaymentCompensation compensation = PaymentCompensation.pending(PAYMENT_KEY, order, TOSS_ORDER_ID,
					approvedAt, REASON);
			given(repository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(compensation));
			for (int i = 0; i < 9; i++) {
				compensation.recordFailure("이전 실패");
			}

			// when
			writer.fail(PAYMENT_KEY, "TOSS 통신 실패");

			// then
			assertThat(compensation.getAttempts()).isEqualTo(10);
			assertThat(compensation.getStatus()).isEqualTo(PaymentCompensationStatus.MANUAL_REVIEW);
		}
	}

	@Nested
	@DisplayName("reviewManually()")
	class ReviewManually {

		@Test
		@DisplayName("상한과 무관하게 즉시 수동 확인으로 남긴다")
		void marksManualReviewImmediately() {
			// given
			PaymentCompensation compensation = PaymentCompensation.pending(PAYMENT_KEY, order, TOSS_ORDER_ID,
					approvedAt, REASON);
			given(repository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(compensation));

			// when
			writer.reviewManually(PAYMENT_KEY, "TOSS 거절");

			// then
			assertThat(compensation.getStatus()).isEqualTo(PaymentCompensationStatus.MANUAL_REVIEW);
			assertThat(compensation.getAttempts()).isEqualTo(1);
		}
	}
}
