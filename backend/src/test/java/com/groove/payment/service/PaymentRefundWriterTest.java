package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderClaimFinalizeService;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentCancel;
import com.groove.payment.entity.PaymentCancelStatus;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentCancelRepository;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class PaymentRefundWriterTest {

	private static final Long ORDER_ID = 10L;
	private static final Long PAYMENT_ID = 20L;
	private static final BigDecimal PRICE = new BigDecimal("45000");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 12, 0);

	@Mock
	OrderRepository orderRepository;

	@Mock
	PaymentRepository paymentRepository;

	@Mock
	PaymentCancelRepository paymentCancelRepository;

	@Mock
	OrderClaimFinalizeService orderClaimFinalizeService;

	PaymentRefundWriter writer;
	Payment payment;
	Order order;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-29T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		writer = new PaymentRefundWriter(orderRepository, paymentRepository, paymentCancelRepository,
				orderClaimFinalizeService, clock);
		Member member = MemberFixture.create();
		Artist artist = ArtistFixture.create();
		order = OrderFixture.createWithItem(member, ProductFixture.create(artist, "Kind of Blue", PRICE), 2);
		ReflectionTestUtils.setField(order, "id", ORDER_ID);
		payment = PaymentFixture.approved(order);
		ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
	}

	private void stubOrderLock() {
		given(paymentRepository.findOrderIdById(PAYMENT_ID)).willReturn(Optional.of(ORDER_ID));
		given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
	}

	/** Mockito 의 save() 스텁이 IDENTITY 채번을 흉내내도록 저장된 엔티티에 id 를 채워 돌려준다. */
	private PaymentCancel withGeneratedId(InvocationOnMock invocation, Long id) {
		PaymentCancel saved = invocation.getArgument(0);
		ReflectionTestUtils.setField(saved, "id", id);
		return saved;
	}

	@Nested
	@DisplayName("requestRefund()")
	class RequestRefund {

		@Test
		@DisplayName("첫 부분취소면 결제 상태는 그대로 두고 순번 1인 멱등키로 요청 기록만 REQUESTED 로 남긴다")
		void requestsFirstPartialCancelWithoutMovingPaymentStatus() {
			// given
			given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(paymentCancelRepository.countByPaymentId(PAYMENT_ID)).willReturn(0L);
			given(paymentCancelRepository.save(any())).willAnswer(invocation -> withGeneratedId(invocation, 90L));
			ArgumentCaptor<PaymentCancel> captor = ArgumentCaptor.forClass(PaymentCancel.class);

			// when
			PaymentRefundRequest result = writer.requestRefund(PAYMENT_ID, new BigDecimal("10000"), "부분 반품", null);

			// then: payment.status 는 CANCEL_REQUESTED 로 옮기지 않는다 - 대사 스케줄러가 이 상태를
			// legacy 전액취소 재시도 대상으로 집어가는 것을 막기 위해서다
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(result.idempotencyKey()).isEqualTo("cancel-" + payment.getPaymentKey() + "-1");
			assertThat(result.cancelAmount()).isEqualByComparingTo("10000");
			verify(paymentCancelRepository).save(captor.capture());
			assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("cancel-" + payment.getPaymentKey() + "-1");
			assertThat(captor.getValue().getStatus()).isEqualTo(PaymentCancelStatus.REQUESTED);
		}

		@Test
		@DisplayName("이미 취소 이력이 있으면 다음 순번의 멱등키를 쓴다")
		void requestsSecondPartialCancelWithNextSequence() {
			// given
			given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(paymentCancelRepository.countByPaymentId(PAYMENT_ID)).willReturn(1L);
			given(paymentCancelRepository.save(any())).willAnswer(invocation -> withGeneratedId(invocation, 91L));

			// when
			PaymentRefundRequest result = writer.requestRefund(PAYMENT_ID, new BigDecimal("5000"), "추가 반품", null);

			// then
			assertThat(result.idempotencyKey()).isEqualTo("cancel-" + payment.getPaymentKey() + "-2");
		}

		@Test
		@DisplayName("PARTIAL_CANCELED 결제에도 추가 부분취소를 요청할 수 있다")
		void requestsAdditionalPartialCancelFromPartiallyCanceledPayment() {
			// given
			payment.applyPartialCancel(new BigDecimal("10000"), NOW);
			given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(paymentCancelRepository.countByPaymentId(PAYMENT_ID)).willReturn(1L);
			given(paymentCancelRepository.save(any())).willAnswer(invocation -> withGeneratedId(invocation, 91L));

			// when
			PaymentRefundRequest result = writer.requestRefund(PAYMENT_ID, new BigDecimal("5000"), "추가 반품", null);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
			assertThat(result.cancelAmount()).isEqualByComparingTo("5000");
		}

		@Test
		@DisplayName("같은 결제에 진행 중(REQUESTED)인 취소 건이 있으면 PAYMENT_CANCEL_IN_PROGRESS 예외를 던진다")
		void throwsWhenAnotherCancelIsInProgress() {
			// given
			given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(paymentCancelRepository.existsByPaymentIdAndStatus(PAYMENT_ID, PaymentCancelStatus.REQUESTED))
					.willReturn(true);

			// when & then
			assertThatThrownBy(() -> writer.requestRefund(PAYMENT_ID, new BigDecimal("1000"), "사유", null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
			verify(paymentCancelRepository, never()).save(any());
		}

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"READY", "CANCELED", "FAILED", "UNKNOWN",
			"CANCEL_REQUESTED"})
		@DisplayName("DONE·PARTIAL_CANCELED 가 아니면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsWhenNotDoneOrPartiallyCanceled(PaymentStatus status) {
			// given
			PaymentFixture.withStatus(payment, status);
			given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when & then
			assertThatThrownBy(() -> writer.requestRefund(PAYMENT_ID, new BigDecimal("1000"), "사유", null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}

		@Test
		@DisplayName("남은 금액을 초과해 요청하면 예외를 던지고 상태를 바꾸지 않는다")
		void throwsWhenExceedsRemainingAmount() {
			// given
			given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));
			BigDecimal tooMuch = payment.getAmount().add(BigDecimal.ONE);

			// when & then
			assertThatThrownBy(() -> writer.requestRefund(PAYMENT_ID, tooMuch, "사유", null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDS_BALANCE);
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
		}

		@Test
		@DisplayName("취소 금액이 0 이하면 예외를 던진다")
		void throwsWhenAmountIsNotPositive() {
			// given
			given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when & then
			assertThatThrownBy(() -> writer.requestRefund(PAYMENT_ID, BigDecimal.ZERO, "사유", null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDS_BALANCE);
		}

		@Test
		@DisplayName("클레임 환불이면 결제 락보다 먼저 클레임을 잠그고 orderClaimId 를 기록한다")
		void locksClaimBeforePaymentAndRecordsClaimId() {
			// given
			given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(paymentCancelRepository.save(any())).willAnswer(invocation -> withGeneratedId(invocation, 90L));
			ArgumentCaptor<PaymentCancel> captor = ArgumentCaptor.forClass(PaymentCancel.class);

			// when
			PaymentRefundRequest result = writer.requestRefund(PAYMENT_ID, new BigDecimal("10000"), "사유", null,
					500L);

			// then
			InOrder inOrder = inOrder(orderClaimFinalizeService, paymentRepository);
			inOrder.verify(orderClaimFinalizeService).lockRefundableClaim(500L);
			inOrder.verify(paymentRepository).findByIdForUpdate(PAYMENT_ID);
			verify(paymentCancelRepository).save(captor.capture());
			assertThat(captor.getValue().getOrderClaimId()).isEqualTo(500L);
			assertThat(result.orderClaimId()).isEqualTo(500L);
		}

		@Test
		@DisplayName("같은 클레임으로 REQUESTED 나 DONE 환불 행이 있으면 ORDER_CLAIM_REFUND_IN_PROGRESS 예외를 던진다")
		void throwsWhenClaimAlreadyHasRefund() {
			// given
			given(paymentCancelRepository.existsByOrderClaimIdAndStatusIn(500L,
					List.of(PaymentCancelStatus.REQUESTED, PaymentCancelStatus.DONE))).willReturn(true);

			// when & then
			assertThatThrownBy(() -> writer.requestRefund(PAYMENT_ID, new BigDecimal("1000"), "사유", null, 500L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);
			verify(paymentRepository, never()).findByIdForUpdate(any());
			verify(paymentCancelRepository, never()).save(any());
		}

		@Test
		@DisplayName("클레임 락에서 진행 중이 아니라고 판정하면 그 예외를 그대로 던지고 결제를 잠그지 않는다")
		void propagatesClaimLockFailure() {
			// given
			willThrow(new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED))
					.given(orderClaimFinalizeService).lockRefundableClaim(500L);

			// when & then
			assertThatThrownBy(() -> writer.requestRefund(PAYMENT_ID, new BigDecimal("1000"), "사유", null, 500L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
			verify(paymentRepository, never()).findByIdForUpdate(any());
		}

		@Test
		@DisplayName("클레임이 없는 환불이면 클레임 락과 클레임 환불 행 조회를 건너뛴다")
		void skipsClaimGuardWithoutClaimId() {
			// given
			given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(paymentCancelRepository.save(any())).willAnswer(invocation -> withGeneratedId(invocation, 90L));

			// when
			writer.requestRefund(PAYMENT_ID, new BigDecimal("1000"), "사유", null);

			// then
			verify(orderClaimFinalizeService, never()).lockRefundableClaim(any());
			verify(paymentCancelRepository, never()).existsByOrderClaimIdAndStatusIn(any(), any());
		}

		@Test
		@DisplayName("결제가 없으면 PAYMENT_NOT_FOUND 예외를 던진다")
		void throwsWhenPaymentNotFound() {
			// given
			given(paymentRepository.findByIdForUpdate(PAYMENT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.requestRefund(PAYMENT_ID, new BigDecimal("1000"), "사유", null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("completeRefund()")
	class CompleteRefund {

		@Test
		@DisplayName("DONE 결제의 취소 누적액을 반영하고 취소 요청 기록을 DONE 으로 남긴다")
		void appliesCancelAndCompletesRequest() {
			// given: T1 에서 payment.status 를 바꾸지 않으므로 DONE 그대로다
			stubOrderLock();
			PaymentCancel paymentCancel = PaymentCancel.request(payment, "cancel-key-1", new BigDecimal("10000"),
					"사유", NOW);
			ReflectionTestUtils.setField(paymentCancel, "id", 90L);
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(paymentCancelRepository.findById(90L)).willReturn(Optional.of(paymentCancel));

			// when
			writer.completeRefund(PAYMENT_ID, 90L, new BigDecimal("10000"), "txn-1", NOW);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
			assertThat(payment.getCanceledAmount()).isEqualByComparingTo("10000");
			assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.DONE);
			assertThat(paymentCancel.getTossTransactionKey()).isEqualTo("txn-1");
			verify(orderClaimFinalizeService, never()).applyRefundDone(any(), any());
		}

		@Test
		@DisplayName("클레임 환불이면 같은 트랜잭션에서 클레임 마무리를 먼저 호출한 뒤 결제에 반영한다")
		void finalizesClaimBeforeApplyingPayment() {
			// given
			stubOrderLock();
			PaymentCancel paymentCancel = PaymentCancel.requestForClaim(payment, "cancel-key-1",
					new BigDecimal("10000"), "사유", NOW, 500L);
			ReflectionTestUtils.setField(paymentCancel, "id", 90L);
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
			given(paymentCancelRepository.findById(90L)).willReturn(Optional.of(paymentCancel));

			// when
			writer.completeRefund(PAYMENT_ID, 90L, new BigDecimal("10000"), "txn-1", NOW);

			// then
			verify(orderClaimFinalizeService).applyRefundDone(500L, NOW);
			assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.DONE);
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
		}

		@Test
		@DisplayName("클레임 마무리가 실패하면 예외를 전파하고 취소 건을 REQUESTED 로 남긴다")
		void keepsRequestedWhenClaimFinalizeFails() {
			// given
			stubOrderLock();
			PaymentCancel paymentCancel = PaymentCancel.requestForClaim(payment, "cancel-key-1",
					new BigDecimal("10000"), "사유", NOW, 500L);
			ReflectionTestUtils.setField(paymentCancel, "id", 90L);
			given(paymentCancelRepository.findById(90L)).willReturn(Optional.of(paymentCancel));
			willThrow(new IllegalStateException("boom")).given(orderClaimFinalizeService).applyRefundDone(500L, NOW);

			// when & then
			assertThatThrownBy(() -> writer.completeRefund(PAYMENT_ID, 90L, new BigDecimal("10000"), "txn-1", NOW))
					.isInstanceOf(IllegalStateException.class);
			assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.REQUESTED);
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
		}

		@Test
		@DisplayName("이미 DONE 인 취소 건이면 클레임 마무리와 취소 누적액 반영을 하지 않는다")
		void skipsWhenAlreadyDone() {
			// given
			stubOrderLock();
			PaymentCancel paymentCancel = PaymentCancel.requestForClaim(payment, "cancel-key-1",
					new BigDecimal("10000"), "사유", NOW, 500L);
			ReflectionTestUtils.setField(paymentCancel, "id", 90L);
			paymentCancel.complete("txn-1", NOW);
			given(paymentCancelRepository.findById(90L)).willReturn(Optional.of(paymentCancel));

			// when
			writer.completeRefund(PAYMENT_ID, 90L, new BigDecimal("10000"), "txn-2", NOW);

			// then
			assertThat(payment.getCanceledAmount()).isEqualByComparingTo("0");
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(paymentCancel.getTossTransactionKey()).isEqualTo("txn-1");
			verify(orderClaimFinalizeService, never()).applyRefundDone(any(), any());
		}

		@Test
		@DisplayName("FAILED 로 기록된 취소 건이면 반영하지 않고 상태를 그대로 둔다")
		void skipsWhenAlreadyFailed() {
			// given
			stubOrderLock();
			PaymentCancel paymentCancel = PaymentCancel.requestForClaim(payment, "cancel-key-1",
					new BigDecimal("10000"), "사유", NOW, 500L);
			ReflectionTestUtils.setField(paymentCancel, "id", 90L);
			paymentCancel.fail();
			given(paymentCancelRepository.findById(90L)).willReturn(Optional.of(paymentCancel));

			// when
			writer.completeRefund(PAYMENT_ID, 90L, new BigDecimal("10000"), "txn-1", NOW);

			// then
			assertThat(payment.getCanceledAmount()).isEqualByComparingTo("0");
			assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
			verify(orderClaimFinalizeService, never()).applyRefundDone(any(), any());
		}

		@Test
		@DisplayName("결제가 없으면 PAYMENT_NOT_FOUND 예외를 던지고 주문을 잠그지 않는다")
		void throwsWhenPaymentNotFound() {
			// given
			given(paymentRepository.findOrderIdById(PAYMENT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.completeRefund(PAYMENT_ID, 90L, new BigDecimal("10000"), "txn-1", NOW))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
			verify(orderRepository, never()).findByIdForUpdate(any());
		}
	}

	@Nested
	@DisplayName("failRefund()")
	class FailRefund {

		@Test
		@DisplayName("취소 요청 기록을 FAILED 로 남기고 결제 상태는 건드리지 않는다")
		void failsRequestWithoutTouchingPayment() {
			// given
			PaymentCancel paymentCancel = PaymentCancel.request(payment, "cancel-key-1", new BigDecimal("10000"),
					"사유", NOW);
			ReflectionTestUtils.setField(paymentCancel, "id", 90L);
			given(paymentCancelRepository.findById(90L)).willReturn(Optional.of(paymentCancel));

			// when
			writer.failRefund(90L);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
		}
	}
}
