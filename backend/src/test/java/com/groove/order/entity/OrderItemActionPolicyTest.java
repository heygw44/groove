package com.groove.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class OrderItemActionPolicyTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 12, 0, 0);

	@Nested
	@DisplayName("resolve() - 클레임이 없을 때")
	class ResolveWithoutClaim {

		@Test
		@DisplayName("PAYMENT_WAITING 이면 CANCEL 만 반환한다")
		void returnsCancelForPaymentWaiting() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.PAYMENT_WAITING, null,
					null, false, NOW);

			// then
			assertThat(actions).containsExactly(OrderItemAction.CANCEL);
		}

		@Test
		@DisplayName("PAID 면 CANCEL 만 반환한다")
		void returnsCancelForPaid() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.PAID, null, null, false,
					NOW);

			// then
			assertThat(actions).containsExactly(OrderItemAction.CANCEL);
		}

		@Test
		@DisplayName("PREPARING 이면 CANCEL_REQUEST 만 반환한다")
		void returnsCancelRequestForPreparing() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.PREPARING, null, null,
					false, NOW);

			// then
			assertThat(actions).containsExactly(OrderItemAction.CANCEL_REQUEST);
		}

		@Test
		@DisplayName("SHIPPING 이면 송장이 없어도 CONFIRM 만 반환한다")
		void returnsConfirmForShippingWithoutTracking() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.SHIPPING, null, null,
					false, NOW);

			// then
			assertThat(actions).containsExactly(OrderItemAction.CONFIRM);
		}

		@Test
		@DisplayName("DELIVERED 이고 배송완료 7일이 지났으면 RETURN_REQUEST 없이 CONFIRM 만 반환한다")
		void returnsConfirmOnlyForDeliveredAfterReturnPeriod() {
			// given
			LocalDateTime deliveredAt = NOW.minusDays(OrderItemActionPolicy.RETURN_PERIOD_DAYS).minusSeconds(1);

			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.DELIVERED, null,
					deliveredAt, false, NOW);

			// then: 리뷰는 구매확정(PURCHASE_CONFIRMED) 부터라 DELIVERED 는 WRITE_REVIEW 를 아직 주지 않는다
			assertThat(actions).containsExactly(OrderItemAction.CONFIRM);
		}

		@Test
		@DisplayName("DELIVERED 이고 배송완료 7일 이내면 RETURN_REQUEST·CONFIRM 을 이 순서로 반환한다")
		void returnsConfirmAndReturnRequestForDeliveredWithinReturnPeriod() {
			// given
			LocalDateTime deliveredAt = NOW.minusDays(1);

			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.DELIVERED, null,
					deliveredAt, false, NOW);

			// then
			assertThat(actions).containsExactly(OrderItemAction.RETURN_REQUEST, OrderItemAction.CONFIRM);
		}

		@Test
		@DisplayName("PURCHASE_CONFIRMED 면 WRITE_REVIEW 만 반환한다")
		void returnsWriteReviewForPurchaseConfirmed() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.PURCHASE_CONFIRMED, null,
					null, false, NOW);

			// then
			assertThat(actions).containsExactly(OrderItemAction.WRITE_REVIEW);
		}

		@ParameterizedTest
		@CsvSource({"CANCELED", "CANCELED_BY_NOPAYMENT", "RETURNED", "PAYMENT_PENDING"})
		@DisplayName("종결·내부 상태면 아무 동작도 반환하지 않는다")
		void returnsNoActionsForTerminalOrInternalStatuses(OrderItemStatus status) {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(status, null, null, false, NOW);

			// then
			assertThat(actions).isEmpty();
		}
	}

	@Nested
	@DisplayName("resolve() - 반품 요청 기한(D7) 경계")
	class ReturnPeriodBoundary {

		@Test
		@DisplayName("배송완료 정확히 7일 뒤(경계 포함)면 RETURN_REQUEST 를 포함한다")
		void includesReturnRequestExactlyAtDeadline() {
			// given
			LocalDateTime deliveredAt = NOW.minusDays(OrderItemActionPolicy.RETURN_PERIOD_DAYS);

			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.DELIVERED, null,
					deliveredAt, false, NOW);

			// then
			assertThat(actions).contains(OrderItemAction.RETURN_REQUEST);
		}

		@Test
		@DisplayName("배송완료 7일 뒤에서 1초라도 지나면 RETURN_REQUEST 를 제외한다")
		void excludesReturnRequestOneSecondAfterDeadline() {
			// given
			LocalDateTime deliveredAt = NOW.minusDays(OrderItemActionPolicy.RETURN_PERIOD_DAYS).minusSeconds(1);

			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.DELIVERED, null,
					deliveredAt, false, NOW);

			// then
			assertThat(actions).doesNotContain(OrderItemAction.RETURN_REQUEST);
		}

		@Test
		@DisplayName("배송완료 시각이 없으면 RETURN_REQUEST 를 반환하지 않는다")
		void excludesReturnRequestWhenDeliveredAtIsNull() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.DELIVERED, null, null,
					false, NOW);

			// then
			assertThat(actions).doesNotContain(OrderItemAction.RETURN_REQUEST);
		}
	}

	@Nested
	@DisplayName("resolve() - 진행 중인 클레임이 있을 때")
	class ResolveWithInProgressClaim {

		@ParameterizedTest
		@EnumSource(value = OrderItemClaimStatus.class, names = {"CANCEL_REQUEST", "RETURN_REQUEST"})
		@DisplayName("CANCEL_REQUEST·RETURN_REQUEST 이면 WITHDRAW_CLAIM 만 반환하고 CANCEL·CANCEL_REQUEST 는 감춘다")
		void returnsWithdrawClaimOnlyForRequestedClaims(OrderItemClaimStatus claimStatus) {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.PREPARING, claimStatus,
					null, false, NOW);

			// then
			assertThat(actions).containsExactly(OrderItemAction.WITHDRAW_CLAIM);
		}

		@Test
		@DisplayName("수거중(COLLECTING)이면 철회를 포함해 클레임 관련 액션을 하나도 주지 않는다")
		void returnsNoClaimActionWhileCollecting() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.DELIVERED,
					OrderItemClaimStatus.COLLECTING, NOW.minusDays(1), false, NOW);

			// then: DELIVERED 는 아직 리뷰 작성 자격(PURCHASE_CONFIRMED)이 아니라 그 어떤 액션도 없다
			assertThat(actions).isEmpty();
		}

		@Test
		@DisplayName("진행 중인 클레임이 있어도 송장이 있으면 TRACK 은 그대로 내려준다")
		void keepsTrackEvenWhenClaimInProgress() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.SHIPPING,
					OrderItemClaimStatus.CANCEL_REQUEST, null, true, NOW);

			// then
			assertThat(actions).containsExactly(OrderItemAction.WITHDRAW_CLAIM, OrderItemAction.TRACK);
		}

		@Test
		@DisplayName("진행 중인 클레임이 있으면 CONFIRM 은 감춘다")
		void hidesConfirmWhenClaimInProgress() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.SHIPPING,
					OrderItemClaimStatus.CANCEL_REQUEST, null, false, NOW);

			// then
			assertThat(actions).doesNotContain(OrderItemAction.CONFIRM);
		}

		@Test
		@DisplayName("진행 중인 클레임이 있으면 배송완료 7일 이내여도 RETURN_REQUEST 는 감춘다")
		void hidesReturnRequestWhenClaimInProgress() {
			// given
			LocalDateTime deliveredAt = NOW.minusDays(1);

			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.DELIVERED,
					OrderItemClaimStatus.RETURN_REQUEST, deliveredAt, false, NOW);

			// then: DELIVERED 는 아직 리뷰 작성 자격(PURCHASE_CONFIRMED)이 아니라 WRITE_REVIEW 는 나오지 않는다
			assertThat(actions).containsExactly(OrderItemAction.WITHDRAW_CLAIM);
		}

		@ParameterizedTest
		@EnumSource(value = OrderItemClaimStatus.class,
				names = {"CANCEL_DONE", "CANCEL_REJECT", "RETURN_DONE", "RETURN_REJECT"})
		@DisplayName("*_DONE·*_REJECT 는 종결된 클레임이라 철회할 수 없고 상태 기준 동작을 그대로 반환한다")
		void treatsTerminalClaimsAsNoActiveClaim(OrderItemClaimStatus claimStatus) {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.PREPARING, claimStatus,
					null, false, NOW);

			// then
			assertThat(actions).containsExactly(OrderItemAction.CANCEL_REQUEST);
		}
	}

	@Nested
	@DisplayName("resolve() - TRACK")
	class Track {

		@ParameterizedTest
		@CsvSource({"SHIPPING", "DELIVERED"})
		@DisplayName("송장이 있고 SHIPPING·DELIVERED 면 TRACK 을 포함한다")
		void includesTrackWhenTrackingNumberExists(OrderItemStatus status) {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(status, null, null, true, NOW);

			// then
			assertThat(actions).contains(OrderItemAction.TRACK);
		}

		@Test
		@DisplayName("송장이 없으면 SHIPPING 이어도 TRACK 을 반환하지 않는다")
		void excludesTrackWhenNoTrackingNumber() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.SHIPPING, null, null,
					false, NOW);

			// then
			assertThat(actions).doesNotContain(OrderItemAction.TRACK);
		}

		@Test
		@DisplayName("송장이 있어도 PAID 처럼 발송 전이면 TRACK 을 반환하지 않는다")
		void excludesTrackBeforeShipping() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.PAID, null, null, true,
					NOW);

			// then
			assertThat(actions).doesNotContain(OrderItemAction.TRACK);
		}
	}

	@Nested
	@DisplayName("resolve() - 리뷰 작성")
	class WriteReview {

		@Test
		@DisplayName("PURCHASE_CONFIRMED 면 WRITE_REVIEW 를 포함한다")
		void includesWriteReviewForPurchaseConfirmed() {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(OrderItemStatus.PURCHASE_CONFIRMED, null,
					null, false, NOW);

			// then
			assertThat(actions).contains(OrderItemAction.WRITE_REVIEW);
		}

		@ParameterizedTest
		@CsvSource({"SHIPPING", "DELIVERED"})
		@DisplayName("SHIPPING·DELIVERED 면 아직 구매확정 전이라 리뷰를 쓸 수 없다")
		void excludesWriteReviewBeforePurchaseConfirmed(OrderItemStatus status) {
			// when
			List<OrderItemAction> actions = OrderItemActionPolicy.resolve(status, null, null, false, NOW);

			// then
			assertThat(actions).doesNotContain(OrderItemAction.WRITE_REVIEW);
		}
	}
}
