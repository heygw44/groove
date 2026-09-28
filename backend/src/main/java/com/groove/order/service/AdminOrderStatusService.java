package com.groove.order.service;

import org.springframework.stereotype.Service;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.dto.AdminOrderDetailResponse;
import com.groove.order.dto.AdminOrderStatusChangeRequest;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.entity.OrderStatus;
import com.groove.payment.client.dto.RefundAccountInfo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminOrderStatusService {

	private final PaidOrderCancelHook paidOrderCancelHook;
	private final AdminOrderService adminOrderService;
	private final AdminAuditLogService adminAuditLogService;

	public AdminOrderDetailResponse changeStatus(Long adminId, Long orderId,
			AdminOrderStatusChangeRequest request) {
		if (request.status() != OrderStatus.CANCELED) {
			adminOrderService.changeStatus(adminId, orderId, request);
			return adminOrderService.getDetail(orderId);
		}

		AdminOrderDetailResponse current = adminOrderService.getDetail(orderId);
		if (!current.status().canTransitionTo(OrderStatus.CANCELED)) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS_TRANSITION);
		}
		RefundAccountInfo refundAccount = toRefundAccount(request.refundAccount());
		PaidOrderCancelResult result = paidOrderCancelHook.cancel(orderId, null, null, refundAccount);
		if (!result.alreadyRequested()) {
			String next = result.status() == PaidOrderCancelStatus.CANCELED ? "CANCELED" : "CANCEL_REQUESTED";
			adminAuditLogService.record(adminId, AdminAuditAction.ORDER_STATUS_CHANGE, AdminAuditTargetType.ORDER,
					orderId, result.previousOrderStatus().name() + "->" + next);
			adminAuditLogService.record(adminId, AdminAuditAction.PAYMENT_CANCEL, AdminAuditTargetType.PAYMENT,
					result.paymentId(), "DONE->" + next);
		}
		return adminOrderService.getDetail(orderId);
	}

	private RefundAccountInfo toRefundAccount(OrderCancelRequest.RefundAccount refundAccount) {
		if (refundAccount == null) {
			return null;
		}
		return new RefundAccountInfo(refundAccount.bankCode(), refundAccount.accountNumber(),
				refundAccount.holderName());
	}
}
