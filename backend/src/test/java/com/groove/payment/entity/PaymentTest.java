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
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("DONE 이면 CANCELED 로 바뀌고 취소 시각이 기록된다")
		void changesStatusToCanceledWhenDone() {
			// given
			Payment payment = PaymentFixture.approved(order());

			// when
			payment.cancel(CANCELED_AT);

			// then
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(payment.getCanceledAt()).isEqualTo(CANCELED_AT);
		}

		@ParameterizedTest
		@EnumSource(value = PaymentStatus.class, names = {"READY", "CANCELED", "FAILED"})
		@DisplayName("DONE 이 아니면 PAYMENT_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatusForNonDoneStatuses(PaymentStatus status) {
			// given
			Payment payment = PaymentFixture.withStatus(Payment.ready(order()), status);

			// when & then
			assertThatThrownBy(() -> payment.cancel(CANCELED_AT))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}
}
