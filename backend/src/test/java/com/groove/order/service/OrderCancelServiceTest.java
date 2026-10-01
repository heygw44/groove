package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderDetailResponse;
import com.groove.order.dto.OrderItemResponse;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.entity.OrderStatus;
import com.groove.payment.client.dto.RefundAccountInfo;
import com.groove.payment.entity.PaymentStatus;

@ExtendWith(MockitoExtension.class)
class OrderCancelServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ORDER_ID = 10L;

	@Mock
	OrderCancelWriter writer;

	@Mock
	PaidOrderCancelHook paidOrderCancelHook;

	@Mock
	PendingVirtualAccountCancelHook pendingVirtualAccountCancelHook;

	@Mock
	OrderItemClaimService orderItemClaimService;

	@Mock
	OrderService orderService;

	OrderCancelService service;

	@BeforeEach
	void setUp() {
		service = new OrderCancelService(writer, paidOrderCancelHook, pendingVirtualAccountCancelHook,
				orderItemClaimService, orderService);
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("결제된 주문이면 결제 취소에 위임하고 이번 요청의 드롭 id 를 응답에 채운다")
		void delegatesPaidOrderCancel() {
			// given
			OrderCancelRequest request = new OrderCancelRequest("고객 변심");
			given(writer.findTarget(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelTarget(OrderStatus.PAID, PaymentStatus.DONE));
			given(writer.planCancel(MEMBER_ID, ORDER_ID)).willReturn(new OrderCancelPlan(List.of(100L), true));
			given(paidOrderCancelHook.cancel(ORDER_ID, MEMBER_ID, request.reason(), null))
					.willReturn(new PaidOrderCancelResult(PaidOrderCancelStatus.CANCELED, false, OrderStatus.PAID,
							20L, 30L));
			given(orderService.getDetailAfterAction(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

			// when
			OrderDetailResponse response = service.cancel(MEMBER_ID, ORDER_ID, request);

			// then
			assertThat(response.limitedDropId()).isEqualTo(30L);
		}

		@Test
		@DisplayName("미결제 주문이면 짧은 트랜잭션에서 취소한다")
		void cancelsUnpaidOrder() {
			// given
			given(writer.findTarget(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelTarget(OrderStatus.PENDING, PaymentStatus.READY));
			given(writer.cancelUnpaid(MEMBER_ID, ORDER_ID, null)).willReturn(UnpaidCancelResult.canceled(30L));
			given(orderService.getDetailAfterAction(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

			// when
			OrderDetailResponse response = service.cancel(MEMBER_ID, ORDER_ID, null);

			// then
			assertThat(response.limitedDropId()).isEqualTo(30L);
			verify(writer).cancelUnpaid(MEMBER_ID, ORDER_ID, null);
		}

		@Test
		@DisplayName("락 대기 중 승인이 끝났으면 결제 취소로 이어간다")
		void delegatesWhenPaymentCompletesAfterLookup() {
			// given
			given(writer.findTarget(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelTarget(OrderStatus.PENDING, PaymentStatus.READY));
			given(writer.cancelUnpaid(MEMBER_ID, ORDER_ID, null))
					.willReturn(UnpaidCancelResult.paymentCancelRequired());
			given(paidOrderCancelHook.cancel(ORDER_ID, MEMBER_ID, null, null))
					.willReturn(new PaidOrderCancelResult(PaidOrderCancelStatus.CANCELED, false, OrderStatus.PAID,
							20L, null));
			given(orderService.getDetailAfterAction(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

			// when
			service.cancel(MEMBER_ID, ORDER_ID, null);

			// then
			verify(paidOrderCancelHook).cancel(ORDER_ID, MEMBER_ID, null, null);
		}

		@Test
		@DisplayName("환불계좌가 있으면 결제 취소 훅에 변환해 전달한다")
		void convertsRefundAccountWhenPresent() {
			// given
			OrderCancelRequest.RefundAccount refundAccount = new OrderCancelRequest.RefundAccount("088",
					"12345678901234", "홍길동");
			OrderCancelRequest request = new OrderCancelRequest("고객 변심", refundAccount);
			given(writer.findTarget(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelTarget(OrderStatus.PAID, PaymentStatus.DONE));
			given(writer.planCancel(MEMBER_ID, ORDER_ID)).willReturn(new OrderCancelPlan(List.of(100L), true));
			given(paidOrderCancelHook.cancel(ORDER_ID, MEMBER_ID, "고객 변심",
					new RefundAccountInfo("088", "12345678901234", "홍길동")))
					.willReturn(new PaidOrderCancelResult(PaidOrderCancelStatus.CANCELED, false, OrderStatus.PAID,
							20L, null));
			given(orderService.getDetailAfterAction(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

			// when
			service.cancel(MEMBER_ID, ORDER_ID, request);

			// then
			verify(paidOrderCancelHook).cancel(ORDER_ID, MEMBER_ID, "고객 변심",
					new RefundAccountInfo("088", "12345678901234", "홍길동"));
		}

		@Test
		@DisplayName("전액취소 대상이 아니면(PREPARING 혼재·클레임 이력 등) 상품 단위 클레임으로 각각 처리한다")
		void cancelsPerItemWhenPreparingItemIsMixedIn() {
			// given
			OrderCancelRequest request = new OrderCancelRequest("고객 변심");
			given(writer.findTarget(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelTarget(OrderStatus.PAID, PaymentStatus.DONE));
			given(writer.planCancel(MEMBER_ID, ORDER_ID)).willReturn(new OrderCancelPlan(List.of(101L, 102L), false));
			given(orderService.getDetailAfterAction(MEMBER_ID, ORDER_ID)).willReturn(detail(null));
			given(orderItemClaimService.cancel(any(), any(), any(), any())).willReturn(itemResponse(false));

			// when
			OrderDetailResponse response = service.cancel(MEMBER_ID, ORDER_ID, request);

			// then
			verify(orderItemClaimService).cancel(MEMBER_ID, ORDER_ID, 101L, request);
			verify(orderItemClaimService).cancel(MEMBER_ID, ORDER_ID, 102L, request);
			verify(paidOrderCancelHook, never()).cancel(any(), any(), any(), any());
			assertThat(response.limitedDropId()).isNull();
		}

		@Test
		@DisplayName("결제가 PARTIAL_CANCELED 면(상품 하나를 먼저 취소한 뒤) 결제 있는 취소 경로로 가되 남은 상품만 처리한다")
		void cancelsRemainingItemsWhenPaymentAlreadyPartiallyCanceled() {
			// given
			OrderCancelRequest request = new OrderCancelRequest("고객 변심");
			given(writer.findTarget(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelTarget(OrderStatus.PAID, PaymentStatus.PARTIAL_CANCELED));
			given(writer.planCancel(MEMBER_ID, ORDER_ID)).willReturn(new OrderCancelPlan(List.of(102L), false));
			given(orderService.getDetailAfterAction(MEMBER_ID, ORDER_ID)).willReturn(detail(null));
			given(orderItemClaimService.cancel(any(), any(), any(), any())).willReturn(itemResponse(false));

			// when
			service.cancel(MEMBER_ID, ORDER_ID, request);

			// then
			verify(orderItemClaimService).cancel(MEMBER_ID, ORDER_ID, 102L, request);
			verify(paidOrderCancelHook, never()).cancel(any(), any(), any(), any());
			verify(writer, never()).cancelUnpaid(any(), any(), any());
		}

		@Test
		@DisplayName("한 상품의 환불이 결과를 기다리는 중이면 남은 상품은 취소하지 않고 멈춘다")
		void stopsAtItemWithRefundInProgress() {
			// given
			OrderCancelRequest request = new OrderCancelRequest("고객 변심");
			given(writer.findTarget(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelTarget(OrderStatus.PAID, PaymentStatus.DONE));
			given(writer.planCancel(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelPlan(List.of(101L, 102L, 103L), false));
			given(orderItemClaimService.cancel(MEMBER_ID, ORDER_ID, 101L, request)).willReturn(itemResponse(false));
			given(orderItemClaimService.cancel(MEMBER_ID, ORDER_ID, 102L, request)).willReturn(itemResponse(true));
			given(orderService.getDetailAfterAction(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

			// when
			service.cancel(MEMBER_ID, ORDER_ID, request);

			// then
			verify(orderItemClaimService).cancel(MEMBER_ID, ORDER_ID, 101L, request);
			verify(orderItemClaimService).cancel(MEMBER_ID, ORDER_ID, 102L, request);
			verify(orderItemClaimService, never()).cancel(MEMBER_ID, ORDER_ID, 103L, request);
		}

		@Test
		@DisplayName("앞선 상품을 취소한 뒤 다음 상품이 실패하면 예외 없이 멈추고 현재 상태를 응답한다")
		void returnsDetailWhenLaterItemFailsAfterEarlierCanceled() {
			// given
			OrderCancelRequest request = new OrderCancelRequest("고객 변심");
			given(writer.findTarget(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelTarget(OrderStatus.PAID, PaymentStatus.DONE));
			given(writer.planCancel(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelPlan(List.of(101L, 102L, 103L), false));
			given(orderItemClaimService.cancel(MEMBER_ID, ORDER_ID, 101L, request)).willReturn(itemResponse(false));
			given(orderItemClaimService.cancel(MEMBER_ID, ORDER_ID, 102L, request))
					.willThrow(new IllegalStateException("환불 실패"));
			given(orderService.getDetailAfterAction(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

			// when
			OrderDetailResponse response = service.cancel(MEMBER_ID, ORDER_ID, request);

			// then
			assertThat(response).isNotNull();
			verify(orderItemClaimService, never()).cancel(MEMBER_ID, ORDER_ID, 103L, request);
		}

		@Test
		@DisplayName("첫 상품부터 실패하면 예외를 그대로 던진다")
		void propagatesWhenFirstItemFails() {
			// given
			OrderCancelRequest request = new OrderCancelRequest("고객 변심");
			given(writer.findTarget(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelTarget(OrderStatus.PAID, PaymentStatus.DONE));
			given(writer.planCancel(MEMBER_ID, ORDER_ID)).willReturn(new OrderCancelPlan(List.of(101L, 102L), false));
			given(orderItemClaimService.cancel(MEMBER_ID, ORDER_ID, 101L, request))
					.willThrow(new IllegalStateException("환불 실패"));

			// when & then
			assertThatThrownBy(() -> service.cancel(MEMBER_ID, ORDER_ID, request))
					.isInstanceOf(IllegalStateException.class);
			verify(orderItemClaimService, never()).cancel(MEMBER_ID, ORDER_ID, 102L, request);
			verify(orderService, never()).getDetailAfterAction(any(), any());
		}

		@Test
		@DisplayName("입금 전 가상계좌면 가상계좌 취소 훅에 위임한다")
		void delegatesPendingVirtualAccountCancel() {
			// given
			given(writer.findTarget(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelTarget(OrderStatus.PENDING, PaymentStatus.WAITING_FOR_DEPOSIT));
			given(pendingVirtualAccountCancelHook.cancel(ORDER_ID, MEMBER_ID, null)).willReturn(30L);
			given(orderService.getDetailAfterAction(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

			// when
			OrderDetailResponse response = service.cancel(MEMBER_ID, ORDER_ID, null);

			// then
			assertThat(response.limitedDropId()).isEqualTo(30L);
			verify(writer, never()).cancelUnpaid(any(), any(), any());
		}
	}

	private OrderItemResponse itemResponse(boolean refundInProgress) {
		return new OrderItemResponse(101L, 1L, "Kind of Blue", BigDecimal.TEN, 1, BigDecimal.TEN, null,
				"20260913-TESTAB12-01", OrderItemStatus.PAID, null, BigDecimal.TEN, null, null, null, List.of(), null,
				refundInProgress);
	}

	private OrderDetailResponse detail(Long limitedDropId) {
		return new OrderDetailResponse(ORDER_ID, "20260913-TESTAB12", OrderStatus.CANCELED, null, null, null, null,
				null, null, null, null, null, null, limitedDropId, null);
	}
}
