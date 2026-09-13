package com.groove.payment.entity;

import static com.groove.fixture.PaymentFixture.APPROVED_AT;
import static com.groove.fixture.PaymentFixture.CANCELED_AT;
import static com.groove.fixture.PaymentFixture.METHOD;
import static com.groove.fixture.PaymentFixture.PAYMENT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import com.groove.product.entity.Artist;

class PaymentTest {

	private static final BigDecimal PRICE = new BigDecimal("45000");

	private final Member member = MemberFixture.create();
	private final Artist artist = ArtistFixture.create();

	private Order order() {
		return OrderFixture.createWithItem(member, ProductFixture.create(artist, "Kind of Blue", PRICE), 2);
	}

	@Nested
	@DisplayName("ready()")
	class Ready {

		@Test
		@DisplayName("생성하면 READY 상태이고 주문번호와 최종 결제 금액을 복사한다")
		void createsWithReadyStatusCopyingOrderNumberAndFinalAmount() {
			// given
			Order order = order();

			// when
			Payment payment = Payment.ready(order);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
			assertThat(payment.getTossOrderId()).isEqualTo(order.getOrderNumber());
			assertThat(payment.getAmount()).isEqualByComparingTo(order.getFinalAmount());
			assertThat(payment.getPaymentKey()).isNull();
			assertThat(payment.getApprovedAt()).isNull();
		}
	}

	@Nested
	@DisplayName("approve()")
	class Approve {

		@Test
		@DisplayName("READY 면 DONE 으로 바뀌고 승인 정보가 기록된다")
		void changesStatusToDoneWhenReady() {
			// given
			Payment payment = Payment.ready(order());

			// when
			payment.approve(PAYMENT_KEY, METHOD, APPROVED_AT);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(payment.getPaymentKey()).isEqualTo(PAYMENT_KEY);
			assertThat(payment.getMethod()).isEqualTo(METHOD);
			assertThat(payment.getApprovedAt()).isEqualTo(APPROVED_AT);
		}

		@Test
		@DisplayName("FAILED 면 재승인되고 이전 실패 사유가 지워진다")
		void approvesAgainAndClearsFailReasonWhenFailed() {
			// given
			Payment payment = PaymentFixture.failed(order(), "TOSS REJECT_CARD_COMPANY: 카드사에서 승인을 거절했습니다.");

			// when
			payment.approve(PAYMENT_KEY, METHOD, APPROVED_AT);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(payment.getFailReason()).isNull();
		}

		@Test
		@DisplayName("이미 DONE 이면 PAYMENT_ALREADY_DONE 예외를 던진다")
		void throwsAlreadyDoneWhenDone() {
			// given
			Payment payment = PaymentFixture.approved(order());

			// when & then
			assertThatThrownBy(() -> payment.approve(PAYMENT_KEY, METHOD, APPROVED_AT))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_ALREADY_DONE);
		}

		@Test
		@DisplayName("CANCELED 면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatusWhenCanceled() {
			// given
			Payment payment = PaymentFixture.canceled(order());

			// when & then
			assertThatThrownBy(() -> payment.approve(PAYMENT_KEY, METHOD, APPROVED_AT))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}

		@Test
		@DisplayName("UNKNOWN 이면 재승인되고 DONE 으로 바뀐다")
		void approvesAgainAndChangesStatusToDoneWhenUnknown() {
			// given
			Payment payment = PaymentFixture.unknown(order(), "TOSS 응답 지연");

			// when
			payment.approve(PAYMENT_KEY, METHOD, APPROVED_AT);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(payment.getFailReason()).isNull();
		}

		@Test
		@DisplayName("CANCEL_REQUESTED 면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatusWhenCancelRequested() {
			// given
			Payment payment = PaymentFixture.withStatus(Payment.ready(order()), PaymentStatus.CANCEL_REQUESTED);

			// when & then
			assertThatThrownBy(() -> payment.approve(PAYMENT_KEY, METHOD, APPROVED_AT))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("fail()")
	class Fail {

		@Test
		@DisplayName("READY 면 FAILED 로 바뀌고 실패 사유가 기록된다")
		void changesStatusToFailedWhenReady() {
			// given
			Payment payment = Payment.ready(order());

			// when
			payment.fail("TOSS REJECT_CARD_COMPANY: 카드사에서 승인을 거절했습니다.");

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
			assertThat(payment.getFailReason()).isEqualTo("TOSS REJECT_CARD_COMPANY: 카드사에서 승인을 거절했습니다.");
		}

		@Test
		@DisplayName("사유가 컬럼 길이를 넘으면 300자로 잘라 저장한다")
		void truncatesFailReasonExceedingColumnLength() {
			// given
			Payment payment = Payment.ready(order());
			String reason = "가".repeat(301);

			// when
			payment.fail(reason);

			// then
			assertThat(payment.getFailReason()).hasSize(300);
		}

		@Test
		@DisplayName("이미 DONE 이면 PAYMENT_ALREADY_DONE 예외를 던진다")
		void throwsAlreadyDoneWhenDone() {
			// given
			Payment payment = PaymentFixture.approved(order());

			// when & then
			assertThatThrownBy(() -> payment.fail("REJECT_CARD_COMPANY"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_ALREADY_DONE);
		}

		@Test
		@DisplayName("CANCELED 면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatusWhenCanceled() {
			// given
			Payment payment = PaymentFixture.canceled(order());

			// when & then
			assertThatThrownBy(() -> payment.fail("REJECT_CARD_COMPANY"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("markUnknown()")
	class MarkUnknown {

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"READY", "FAILED"})
		@DisplayName("READY 또는 FAILED 면 UNKNOWN 으로 바뀌고 사유가 기록된다")
		void changesStatusToUnknown(PaymentStatus status) {
			// given
			Payment payment = PaymentFixture.withStatus(Payment.ready(order()), status);

			// when
			payment.markUnknown("TOSS 응답 지연");

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
			assertThat(payment.getFailReason()).isEqualTo("TOSS 응답 지연");
		}

		@Test
		@DisplayName("사유가 컬럼 길이를 넘으면 300자로 잘라 저장한다")
		void truncatesReasonExceedingColumnLength() {
			// given
			Payment payment = Payment.ready(order());
			String reason = "가".repeat(301);

			// when
			payment.markUnknown(reason);

			// then
			assertThat(payment.getFailReason()).hasSize(300);
		}

		@Test
		@DisplayName("이미 DONE 이면 PAYMENT_ALREADY_DONE 예외를 던진다")
		void throwsAlreadyDoneWhenDone() {
			// given
			Payment payment = PaymentFixture.approved(order());

			// when & then
			assertThatThrownBy(() -> payment.markUnknown("TOSS 응답 지연"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_ALREADY_DONE);
		}

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"CANCELED", "CANCEL_REQUESTED"})
		@DisplayName("CANCELED 또는 CANCEL_REQUESTED 면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatus(PaymentStatus status) {
			// given
			Payment payment = PaymentFixture.withStatus(Payment.ready(order()), status);

			// when & then
			assertThatThrownBy(() -> payment.markUnknown("TOSS 응답 지연"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("requestCancel()")
	class RequestCancel {

		@Test
		@DisplayName("DONE 이면 CANCEL_REQUESTED 로 바뀐다")
		void changesStatusToCancelRequestedWhenDone() {
			// given
			Payment payment = PaymentFixture.approved(order());

			// when
			payment.requestCancel();

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
		}

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"READY", "CANCELED", "FAILED"})
		@DisplayName("DONE 이 아니면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatusForNonDoneStatuses(PaymentStatus status) {
			// given
			Payment payment = PaymentFixture.withStatus(Payment.ready(order()), status);

			// when & then
			assertThatThrownBy(payment::requestCancel)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("completeCancel()")
	class CompleteCancel {

		@Test
		@DisplayName("CANCEL_REQUESTED 면 CANCELED 로 바뀌고 취소 시각이 기록된다")
		void changesStatusToCanceledWhenRequested() {
			// given
			Payment payment = PaymentFixture.approved(order());
			payment.requestCancel();

			// when
			payment.completeCancel(CANCELED_AT);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
		}

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"READY", "DONE", "CANCELED", "FAILED", "UNKNOWN"})
		@DisplayName("CANCEL_REQUESTED 가 아니면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatusWhenNotRequested(PaymentStatus status) {
			// given
			Payment payment = PaymentFixture.withStatus(Payment.ready(order()), status);

			// when & then
			assertThatThrownBy(() -> payment.completeCancel(CANCELED_AT))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("revertCancelRequest()")
	class RevertCancelRequest {

		@Test
		@DisplayName("CANCEL_REQUESTED 면 DONE 으로 되돌린다")
		void changesStatusBackToDone() {
			// given
			Payment payment = PaymentFixture.approved(order());
			payment.requestCancel();

			// when
			payment.revertCancelRequest();

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
		}

		@Test
		@DisplayName("CANCEL_REQUESTED 가 아니면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatusWhenNotRequested() {
			// given
			Payment payment = PaymentFixture.approved(order());

			// when & then
			assertThatThrownBy(payment::revertCancelRequest)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("retry()")
	class Retry {

		@Test
		@DisplayName("FAILED 면 READY 로 바뀌고 실패 사유와 대사 시도 횟수가 초기화된다")
		void resetsToReadyWhenFailed() {
			// given
			Payment payment = PaymentFixture.failed(order(), "TOSS REJECT_CARD_COMPANY");
			payment.recordReconcileMiss();

			// when
			payment.retry();

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
			assertThat(payment.getFailReason()).isNull();
			assertThat(payment.getReconcileAttempts()).isZero();
		}

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"READY", "DONE", "CANCELED", "UNKNOWN", "CANCEL_REQUESTED"})
		@DisplayName("FAILED 가 아니면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatusWhenNotFailed(PaymentStatus status) {
			// given
			Payment payment = PaymentFixture.withStatus(Payment.ready(order()), status);

			// when & then
			assertThatThrownBy(payment::retry)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("compensate()")
	class Compensate {

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"READY", "UNKNOWN", "FAILED"})
		@DisplayName("READY, UNKNOWN, FAILED 면 CANCELED 로 바뀌고 승인·취소 시각과 사유가 채워진다")
		void changesStatusToCanceledAndFillsApprovedAndCanceledAt(PaymentStatus status) {
			// given
			Payment payment = PaymentFixture.withStatus(Payment.ready(order()), status);

			// when
			payment.compensate(PAYMENT_KEY, APPROVED_AT, CANCELED_AT, "승인 후 주문 무효로 자동 취소");

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(payment.getPaymentKey()).isEqualTo(PAYMENT_KEY);
			assertThat(payment.getApprovedAt()).isEqualTo(APPROVED_AT);
			assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
			assertThat(payment.getFailReason()).isEqualTo("승인 후 주문 무효로 자동 취소");
		}

		@Test
		@DisplayName("이미 결제 키와 승인 시각이 있으면 덮어쓰지 않는다")
		void doesNotOverwriteExistingPaymentKeyAndApprovedAt() {
			// given: 이전 대사 라운드에서 결제 키와 승인 시각까지는 채웠지만 CANCELED 로 확정되지 못한 상태를 흉내낸다.
			Payment payment = PaymentFixture.unknown(order(), "TOSS 응답 지연");
			ReflectionTestUtils.setField(payment, "paymentKey", PAYMENT_KEY);
			ReflectionTestUtils.setField(payment, "approvedAt", APPROVED_AT);

			// when
			payment.compensate("other-key", APPROVED_AT.plusMinutes(1), CANCELED_AT, "사유");

			// then
			assertThat(payment.getPaymentKey()).isEqualTo(PAYMENT_KEY);
			assertThat(payment.getApprovedAt()).isEqualTo(APPROVED_AT);
		}

		@Test
		@DisplayName("승인 시각이 없으면 취소 시각으로 채운다")
		void fillsApprovedAtWithCanceledTimeWhenApprovedTimeIsNull() {
			// given
			Payment payment = Payment.ready(order());

			// when
			payment.compensate(PAYMENT_KEY, null, CANCELED_AT, "사유");

			// then
			assertThat(payment.getApprovedAt()).isEqualTo(CANCELED_AT);
		}

		@Test
		@DisplayName("이미 CANCELED 면 아무것도 바꾸지 않는다(멱등)")
		void doesNothingWhenAlreadyCanceled() {
			// given
			Payment payment = PaymentFixture.canceled(order());

			// when
			payment.compensate("other-key", APPROVED_AT.plusMinutes(1), CANCELED_AT.plusMinutes(1), "다른 사유");

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(payment.getPaymentKey()).isEqualTo(PAYMENT_KEY);
			assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
		}

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"DONE", "CANCEL_REQUESTED"})
		@DisplayName("DONE 또는 CANCEL_REQUESTED 면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatus(PaymentStatus status) {
			// given
			Payment payment = PaymentFixture.withStatus(Payment.ready(order()), status);

			// when & then
			assertThatThrownBy(() -> payment.compensate(PAYMENT_KEY, APPROVED_AT, CANCELED_AT, "사유"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("recordReconcileMiss()")
	class RecordReconcileMiss {

		@Test
		@DisplayName("호출할 때마다 대사 시도 횟수가 1씩 늘어난다")
		void incrementsReconcileAttempts() {
			// given
			Payment payment = Payment.ready(order());

			// when
			payment.recordReconcileMiss();
			payment.recordReconcileMiss();

			// then
			assertThat(payment.getReconcileAttempts()).isEqualTo(2);
		}
	}
}
