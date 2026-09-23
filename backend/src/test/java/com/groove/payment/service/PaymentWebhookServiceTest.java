package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.dto.PaymentCompensationCandidate;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentCompensation;
import com.groove.payment.entity.PaymentWebhookEvent;
import com.groove.payment.entity.PaymentWebhookResult;
import com.groove.payment.repository.PaymentCompensationRepository;
import com.groove.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

	private static final Long PAYMENT_ID = 900L;
	private static final Long ORDER_ID = 500L;
	private static final Long EVENT_ID = 7L;
	private static final String TOSS_ORDER_ID = "20260922-ABCDEFGH";
	private static final String PAYMENT_KEY = "webhook-key";

	@Mock
	private PaymentWebhookEventWriter eventWriter;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentCompensationRepository compensationRepository;

	@Mock
	private PaymentCompensationRetrier compensationRetrier;

	@Mock
	private PaymentClient paymentClient;

	@Mock
	private PaymentLateResultApplier lateResultApplier;

	@Mock
	private AlertNotifier alertNotifier;

	private PaymentWebhookService service;

	private Member member;
	private Order order;
	private Payment payment;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
		service = new PaymentWebhookService(objectMapper, eventWriter, paymentRepository, compensationRepository,
				compensationRetrier, paymentClient, lateResultApplier, alertNotifier);

		member = MemberFixture.create();
		order = OrderFixture.withId(OrderFixture.create(member), ORDER_ID);
		payment = Payment.ready(order);
		ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
	}

	@Nested
	@DisplayName("handle()")
	class Handle {

		@Test
		@DisplayName("본문이 JSON 으로 파싱되지 않으면 아무것도 하지 않는다")
		void doesNothingWhenBodyIsUnparsable() {
			// when
			service.handle("이건 JSON 이 아니다 {{{");

			// then
			verifyNoInteractions(eventWriter, paymentRepository, compensationRepository);
		}

		@Test
		@DisplayName("대상 이벤트가 아니면 아무것도 하지 않는다")
		void doesNothingWhenEventTypeNotTarget() {
			// when
			service.handle(body("METHOD_UPDATED", PAYMENT_KEY, TOSS_ORDER_ID, "DONE"));

			// then
			verifyNoInteractions(eventWriter, paymentRepository, compensationRepository);
		}

		@Test
		@DisplayName("data.orderId 가 없으면 아무것도 하지 않는다")
		void doesNothingWhenOrderIdMissing() {
			// when
			service.handle(bodyWithoutOrderId());

			// then
			verifyNoInteractions(eventWriter, paymentRepository, compensationRepository);
		}

		@Test
		@DisplayName("결제도 보상 대기 행도 없으면 아무것도 하지 않는다")
		void doesNothingWhenPaymentAndCompensationBothMissing() {
			// given
			given(paymentRepository.findByTossOrderId(TOSS_ORDER_ID)).willReturn(Optional.empty());
			given(compensationRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.empty());

			// when
			service.handle(body("PAYMENT_STATUS_CHANGED", PAYMENT_KEY, TOSS_ORDER_ID, "DONE"));

			// then
			verifyNoInteractions(eventWriter);
		}

		@Test
		@DisplayName("보상 대기 행이 PENDING 이 아니면 아무것도 하지 않는다")
		void doesNothingWhenCompensationNotPending() {
			// given
			PaymentCompensation compensation = PaymentCompensation.pending(PAYMENT_KEY, order, TOSS_ORDER_ID, null,
					"사유");
			compensation.markManualReview();
			given(paymentRepository.findByTossOrderId(TOSS_ORDER_ID)).willReturn(Optional.empty());
			given(compensationRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(compensation));

			// when
			service.handle(body("PAYMENT_STATUS_CHANGED", PAYMENT_KEY, TOSS_ORDER_ID, "DONE"));

			// then
			verifyNoInteractions(eventWriter);
		}

		@Test
		@DisplayName("보상 대기 행이 PENDING 이면 즉시 회수를 시도하고 APPLIED 로 남긴다")
		void retriesCompensationImmediatelyWhenPending() {
			// given
			PaymentCompensation compensation = PaymentCompensation.pending(PAYMENT_KEY, order, TOSS_ORDER_ID, null,
					"중복 승인 자동 취소");
			given(paymentRepository.findByTossOrderId(TOSS_ORDER_ID)).willReturn(Optional.empty());
			given(compensationRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(compensation));
			given(eventWriter.receive(eq("PAYMENT_STATUS_CHANGED"), eq(PAYMENT_KEY), eq(TOSS_ORDER_ID), eq("DONE"),
					any())).willReturn(Optional.of(eventWithId()));

			// when
			service.handle(body("PAYMENT_STATUS_CHANGED", PAYMENT_KEY, TOSS_ORDER_ID, "DONE"));

			// then
			verify(compensationRetrier)
					.retry(new PaymentCompensationCandidate(PAYMENT_KEY, "중복 승인 자동 취소"));
			verify(eventWriter).markResult(EVENT_ID, PaymentWebhookResult.APPLIED, "payment_compensation 즉시 회수");
		}

		@Test
		@DisplayName("보상 대기 회수 경로도 중복 이벤트면 회수를 시도하지 않는다")
		void doesNotRetryCompensationWhenEventIsDuplicate() {
			// given
			PaymentCompensation compensation = PaymentCompensation.pending(PAYMENT_KEY, order, TOSS_ORDER_ID, null,
					"중복 승인 자동 취소");
			given(paymentRepository.findByTossOrderId(TOSS_ORDER_ID)).willReturn(Optional.empty());
			given(compensationRepository.findByPaymentKey(PAYMENT_KEY)).willReturn(Optional.of(compensation));
			given(eventWriter.receive(any(), any(), any(), any(), any())).willReturn(Optional.empty());

			// when
			service.handle(body("PAYMENT_STATUS_CHANGED", PAYMENT_KEY, TOSS_ORDER_ID, "DONE"));

			// then
			verify(compensationRetrier, never()).retry(any());
		}

		@Test
		@DisplayName("알려진 결제도 중복 이벤트면 재조회하지 않는다")
		void doesNotResolveWhenKnownPaymentEventIsDuplicate() {
			// given
			given(paymentRepository.findByTossOrderId(TOSS_ORDER_ID)).willReturn(Optional.of(payment));
			given(eventWriter.receive(any(), any(), any(), any(), any())).willReturn(Optional.empty());

			// when
			service.handle(body("PAYMENT_STATUS_CHANGED", PAYMENT_KEY, TOSS_ORDER_ID, "DONE"));

			// then
			verifyNoInteractions(paymentClient);
		}

		@Test
		@DisplayName("재조회가 실패하면 ERROR 로 남기고 PAYMENT_RESULT_UNKNOWN 을 던지며, lateResultApplier 는 부르지 않는다")
		void throwsResultUnknownWhenLookupFails() {
			// given
			given(paymentRepository.findByTossOrderId(TOSS_ORDER_ID)).willReturn(Optional.of(payment));
			given(eventWriter.receive(any(), any(), any(), any(), any()))
					.willReturn(Optional.of(eventWithId()));
			RuntimeException cause = new RuntimeException("TOSS 통신 실패");
			given(paymentClient.lookup(TOSS_ORDER_ID)).willThrow(cause);

			// when & then
			assertThatThrownBy(() -> service.handle(body("PAYMENT_STATUS_CHANGED", PAYMENT_KEY, TOSS_ORDER_ID,
					"DONE")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
			verify(eventWriter).markResult(eq(EVENT_ID), eq(PaymentWebhookResult.ERROR), any());
			verifyNoInteractions(lateResultApplier);
		}

		@Test
		@DisplayName("대사 적용이 예외를 던지면 ERROR 로 남기고 PAYMENT_RESULT_UNKNOWN 을 던지며 경보를 보낸다")
		void throwsResultUnknownWhenApplyFails() {
			// given
			given(paymentRepository.findByTossOrderId(TOSS_ORDER_ID)).willReturn(Optional.of(payment));
			given(eventWriter.receive(any(), any(), any(), any(), any()))
					.willReturn(Optional.of(eventWithId()));
			PaymentLookupResult lookup = doneLookup();
			given(paymentClient.lookup(TOSS_ORDER_ID)).willReturn(lookup);
			given(lateResultApplier.apply(any(), eq(lookup), eq("webhook"))).willThrow(new RuntimeException("DB 오류"));

			// when & then
			assertThatThrownBy(() -> service.handle(body("PAYMENT_STATUS_CHANGED", PAYMENT_KEY, TOSS_ORDER_ID,
					"DONE")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
			verify(eventWriter).markResult(eq(EVENT_ID), eq(PaymentWebhookResult.ERROR), any());
			verify(alertNotifier).notify(any(Alert.class));
		}

		@Test
		@DisplayName("알려진 결제 이벤트를 받으면 재조회 결과를 detail=webhook 으로 lateResultApplier 에 위임하고 성공하면 APPLIED 로 남긴다")
		void delegatesToLateResultApplierAndMarksApplied() {
			// given
			given(paymentRepository.findByTossOrderId(TOSS_ORDER_ID)).willReturn(Optional.of(payment));
			given(eventWriter.receive(any(), any(), any(), any(), any()))
					.willReturn(Optional.of(eventWithId()));
			PaymentLookupResult lookup = doneLookup();
			given(paymentClient.lookup(TOSS_ORDER_ID)).willReturn(lookup);
			given(lateResultApplier.apply(any(), eq(lookup), eq("webhook")))
					.willReturn(PaymentReconcileOutcome.applied());

			// when
			service.handle(body("PAYMENT_STATUS_CHANGED", PAYMENT_KEY, TOSS_ORDER_ID, "DONE"));

			// then
			ArgumentCaptor<PaymentReconcileCandidate> captor = ArgumentCaptor
					.forClass(PaymentReconcileCandidate.class);
			verify(lateResultApplier).apply(captor.capture(), eq(lookup), eq("webhook"));
			assertThat(captor.getValue().paymentId()).isEqualTo(PAYMENT_ID);
			assertThat(captor.getValue().orderId()).isEqualTo(ORDER_ID);
			assertThat(captor.getValue().tossOrderId()).isEqualTo(TOSS_ORDER_ID);
			verify(eventWriter).markResult(EVENT_ID, PaymentWebhookResult.APPLIED, "webhook");
		}
	}

	private PaymentWebhookEvent eventWithId() {
		PaymentWebhookEvent event = PaymentWebhookEvent.receive("PAYMENT_STATUS_CHANGED", PAYMENT_KEY, TOSS_ORDER_ID,
				"DONE", null, null);
		ReflectionTestUtils.setField(event, "id", EVENT_ID);
		return event;
	}

	private PaymentLookupResult doneLookup() {
		return new PaymentLookupResult(PaymentLookupStatus.DONE, PAYMENT_KEY, "카드", order.getFinalAmount(),
				null, null);
	}

	private String body(String eventType, String paymentKey, String orderId, String status) {
		return ("{ \"eventType\": \"%s\", \"createdAt\": \"2026-09-22T10:00:00+09:00\", "
				+ "\"data\": { \"paymentKey\": \"%s\", \"orderId\": \"%s\", \"status\": \"%s\" } }")
				.formatted(eventType, paymentKey, orderId, status);
	}

	private String bodyWithoutOrderId() {
		return "{ \"eventType\": \"PAYMENT_STATUS_CHANGED\", \"createdAt\": \"2026-09-22T10:00:00+09:00\", "
				+ "\"data\": { \"paymentKey\": \"webhook-key\", \"status\": \"DONE\" } }";
	}
}
