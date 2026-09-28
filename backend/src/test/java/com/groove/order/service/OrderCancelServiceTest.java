package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderDetailResponse;
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
	OrderService orderService;

	OrderCancelService service;

	@BeforeEach
	void setUp() {
		service = new OrderCancelService(writer, paidOrderCancelHook, pendingVirtualAccountCancelHook, orderService);
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
			given(paidOrderCancelHook.cancel(ORDER_ID, MEMBER_ID, request.reason(), null))
					.willReturn(new PaidOrderCancelResult(PaidOrderCancelStatus.CANCELED, false, OrderStatus.PAID,
							20L, 30L));
			given(orderService.getDetail(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

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
			given(orderService.getDetail(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

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
			given(orderService.getDetail(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

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
			given(paidOrderCancelHook.cancel(ORDER_ID, MEMBER_ID, "고객 변심",
					new RefundAccountInfo("088", "12345678901234", "홍길동")))
					.willReturn(new PaidOrderCancelResult(PaidOrderCancelStatus.CANCELED, false, OrderStatus.PAID,
							20L, null));
			given(orderService.getDetail(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

			// when
			service.cancel(MEMBER_ID, ORDER_ID, request);

			// then
			verify(paidOrderCancelHook).cancel(ORDER_ID, MEMBER_ID, "고객 변심",
					new RefundAccountInfo("088", "12345678901234", "홍길동"));
		}

		@Test
		@DisplayName("입금 전 가상계좌면 가상계좌 취소 훅에 위임한다")
		void delegatesPendingVirtualAccountCancel() {
			// given
			given(writer.findTarget(MEMBER_ID, ORDER_ID))
					.willReturn(new OrderCancelTarget(OrderStatus.PENDING, PaymentStatus.WAITING_FOR_DEPOSIT));
			given(pendingVirtualAccountCancelHook.cancel(ORDER_ID, MEMBER_ID, null)).willReturn(30L);
			given(orderService.getDetail(MEMBER_ID, ORDER_ID)).willReturn(detail(null));

			// when
			OrderDetailResponse response = service.cancel(MEMBER_ID, ORDER_ID, null);

			// then
			assertThat(response.limitedDropId()).isEqualTo(30L);
			verify(writer, never()).cancelUnpaid(any(), any(), any());
		}
	}

	private OrderDetailResponse detail(Long limitedDropId) {
		return new OrderDetailResponse(ORDER_ID, "20260913-TESTAB12", OrderStatus.CANCELED, null, null, null, null,
				null, null, null, null, null, null, limitedDropId, null);
	}
}
