package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.order.dto.AdminOrderDetailResponse;
import com.groove.order.dto.AdminOrderStatusChangeRequest;
import com.groove.order.entity.OrderStatus;

@ExtendWith(MockitoExtension.class)
class AdminOrderStatusServiceTest {

	private static final Long ADMIN_ID = 1L;
	private static final Long ORDER_ID = 10L;
	private static final Long PAYMENT_ID = 20L;

	@Mock
	PaidOrderCancelHook paidOrderCancelHook;

	@Mock
	AdminOrderService adminOrderService;

	@Mock
	AdminAuditLogService adminAuditLogService;

	AdminOrderStatusService service;

	@BeforeEach
	void setUp() {
		service = new AdminOrderStatusService(paidOrderCancelHook, adminOrderService, adminAuditLogService);
		AdminOrderDetailResponse current = org.mockito.Mockito.mock(AdminOrderDetailResponse.class);
		lenient().when(current.status()).thenReturn(OrderStatus.PAID);
		lenient().when(adminOrderService.getDetail(ORDER_ID)).thenReturn(current);
	}

	@Nested
	@DisplayName("changeStatus()")
	class ChangeStatus {

		@Test
		@DisplayName("취소가 완료되면 주문과 결제 감사 로그를 남긴다")
		void recordsCompletedCancelLogs() {
			// given
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.CANCELED);
			given(paidOrderCancelHook.cancel(ORDER_ID, null, null)).willReturn(
					new PaidOrderCancelResult(PaidOrderCancelStatus.CANCELED, false, OrderStatus.PAID,
							PAYMENT_ID, null));

			// when
			service.changeStatus(ADMIN_ID, ORDER_ID, request);

			// then
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.ORDER_STATUS_CHANGE,
					AdminAuditTargetType.ORDER, ORDER_ID, "PAID->CANCELED");
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.PAYMENT_CANCEL,
					AdminAuditTargetType.PAYMENT, PAYMENT_ID, "DONE->CANCELED");
		}

		@Test
		@DisplayName("취소 결과를 알 수 없으면 CANCEL_REQUESTED 감사 로그를 남긴다")
		void recordsInProgressCancelLogs() {
			// given
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.CANCELED);
			given(paidOrderCancelHook.cancel(ORDER_ID, null, null)).willReturn(
					new PaidOrderCancelResult(PaidOrderCancelStatus.IN_PROGRESS, false, OrderStatus.PREPARING,
							PAYMENT_ID, null));

			// when
			service.changeStatus(ADMIN_ID, ORDER_ID, request);

			// then
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.ORDER_STATUS_CHANGE,
					AdminAuditTargetType.ORDER, ORDER_ID, "PREPARING->CANCEL_REQUESTED");
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.PAYMENT_CANCEL,
					AdminAuditTargetType.PAYMENT, PAYMENT_ID, "DONE->CANCEL_REQUESTED");
		}

		@Test
		@DisplayName("이미 요청된 취소면 감사 로그를 중복 기록하지 않는다")
		void skipsLogsForDuplicateRequest() {
			// given
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.CANCELED);
			given(paidOrderCancelHook.cancel(ORDER_ID, null, null)).willReturn(
					new PaidOrderCancelResult(PaidOrderCancelStatus.IN_PROGRESS, true, OrderStatus.PAID,
							PAYMENT_ID, null));

			// when
			service.changeStatus(ADMIN_ID, ORDER_ID, request);

			// then
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("취소가 아니면 기존 상태 전이 서비스에 위임한다")
		void delegatesNonCancelTransition() {
			// given
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.PREPARING);
			AdminOrderDetailResponse detail = org.mockito.Mockito.mock(AdminOrderDetailResponse.class);
			given(adminOrderService.getDetail(ORDER_ID)).willReturn(detail);

			// when
			AdminOrderDetailResponse response = service.changeStatus(ADMIN_ID, ORDER_ID, request);

			// then
			assertThat(response).isSameAs(detail);
			verify(adminOrderService).changeStatus(ADMIN_ID, ORDER_ID, request);
			verify(adminOrderService, times(1)).getDetail(ORDER_ID);
			verify(paidOrderCancelHook, never()).cancel(any(), any(), any());
		}
	}
}
