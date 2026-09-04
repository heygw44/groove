package com.groove.order.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.order.dto.AdminOrderDetailResponse;
import com.groove.order.dto.AdminOrderSearchCondition;
import com.groove.order.dto.AdminOrderSearchRequest;
import com.groove.order.dto.AdminOrderStatusChangeRequest;
import com.groove.order.dto.AdminOrderSummaryResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.mapper.OrderQueryMapper;
import com.groove.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/** 관리자 주문 목록 조회·상태 전이. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminOrderService {

	private final OrderRepository orderRepository;
	private final OrderQueryMapper orderQueryMapper;
	private final OrderStockService orderStockService;
	private final PaymentCancelHook paymentCancelHook;
	private final AdminAuditLogService adminAuditLogService;

	public PageResponse<AdminOrderSummaryResponse> getList(AdminOrderSearchRequest request) {
		AdminOrderSearchCondition condition = request.toCondition();
		long totalElements = orderQueryMapper.countAdminOrders(condition);
		if (totalElements == 0) {
			return PageResponse.of(List.of(), condition.page(), condition.size(), 0);
		}
		List<AdminOrderSummaryResponse> content = orderQueryMapper.findAdminOrders(condition);
		return PageResponse.of(content, condition.page(), condition.size(), totalElements);
	}

	public AdminOrderDetailResponse getDetail(Long orderId) {
		Order order = orderRepository.findWithItemsAndMemberById(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		return AdminOrderDetailResponse.from(order);
	}

	@Transactional
	public AdminOrderDetailResponse changeStatus(Long adminId, Long orderId, AdminOrderStatusChangeRequest request) {
		// 만료 스케줄러와 같은 주문을 동시에 취소하면 재고가 두 번 복구되므로 주문 행을 먼저 잠근다.
		orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Order order = orderRepository.findWithItemsAndMemberById(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		OrderStatus previous = order.getStatus();
		OrderStatus next = request.status();
		order.changeStatus(next);

		if (next == OrderStatus.CANCELED) {
			orderStockService.restore(order);
			restoreCoupon(order);
			if (previous == OrderStatus.PAID || previous == OrderStatus.PREPARING) {
				paymentCancelHook.onPaidOrderCanceled(order);
			}
		}

		adminAuditLogService.record(adminId, AdminAuditAction.ORDER_STATUS_CHANGE, AdminAuditTargetType.ORDER,
				orderId, previous.name() + "->" + next.name());
		return AdminOrderDetailResponse.from(order);
	}

	private void restoreCoupon(Order order) {
		if (order.getMemberCoupon() != null && order.getMemberCoupon().isUsed()) {
			order.getMemberCoupon().restore();
		}
	}
}
