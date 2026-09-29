package com.groove.order.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.order.dto.AdminOrderItemBulkResultResponse;
import com.groove.order.dto.AdminOrderItemConfirmRequest;
import com.groove.order.dto.AdminOrderItemDeliverRequest;
import com.groove.order.dto.AdminOrderItemSearchCondition;
import com.groove.order.dto.AdminOrderItemSearchRequest;
import com.groove.order.dto.AdminOrderItemShipRequest;
import com.groove.order.dto.AdminOrderItemSummaryResponse;
import com.groove.order.entity.CourierCode;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.entity.OrderStatus;
import com.groove.order.mapper.OrderQueryMapper;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 상품주문 목록 조회와 발주확인·발송처리·배송완료 일괄 처리. 대상이 아닌 상품주문(상태가 다르거나 진행 중
 * 클레임이 있음)은 예외로 막지 않고 건너뛰어 처리/건너뜀 건수로만 알려준다 - 관리자가 여러 상태가 섞인 상품주문을
 * 한 번에 선택해도 일부만 유효하면 그만큼은 처리되게 하기 위함이다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminOrderItemService {

	private final OrderQueryMapper orderQueryMapper;
	private final OrderItemRepository orderItemRepository;
	private final OrderRepository orderRepository;
	private final OrderStatusAligner orderStatusAligner;
	private final AdminAuditLogService adminAuditLogService;
	private final Clock clock;

	public PageResponse<AdminOrderItemSummaryResponse> getList(AdminOrderItemSearchRequest request) {
		AdminOrderItemSearchCondition condition = request.toCondition();
		long totalElements = orderQueryMapper.countAdminOrderItems(condition);
		if (totalElements == 0) {
			return PageResponse.of(List.of(), condition.page(), condition.size(), 0);
		}
		List<AdminOrderItemSummaryResponse> content = orderQueryMapper.findAdminOrderItems(condition);
		return PageResponse.of(content, condition.page(), condition.size(), totalElements);
	}

	@Transactional
	public AdminOrderItemBulkResultResponse confirmPreparing(Long adminId, AdminOrderItemConfirmRequest request) {
		List<Long> orderItemIds = request.orderItemIds();
		lockOrders(orderItemIds);
		List<OrderItem> items = orderItemRepository.findAllById(orderItemIds);
		LocalDateTime now = LocalDateTime.now(clock);

		Set<Long> changedOrderIds = new LinkedHashSet<>();
		int processed = 0;
		for (OrderItem item : items) {
			if (item.confirmPreparing(now)) {
				processed++;
				changedOrderIds.add(item.getOrder().getId());
			}
		}
		syncAndAudit(adminId, changedOrderIds, OrderItemStatus.PREPARING, OrderStatus.PREPARING, "PAID->PREPARING");
		return toResult(processed, orderItemIds.size());
	}

	@Transactional
	public AdminOrderItemBulkResultResponse startShipping(Long adminId, AdminOrderItemShipRequest request) {
		// courierCode 파싱은 잠금 전에 전부 끝낸다 - 하나라도 잘못되면 아무 것도 잠그거나 바꾸지 않고 즉시 400.
		Map<Long, CourierCode> courierCodesByItemId = new LinkedHashMap<>();
		Map<Long, String> trackingNumbersByItemId = new LinkedHashMap<>();
		for (AdminOrderItemShipRequest.ShipItem shipItem : request.items()) {
			courierCodesByItemId.put(shipItem.orderItemId(), parseCourierCode(shipItem.courierCode()));
			trackingNumbersByItemId.put(shipItem.orderItemId(), shipItem.trackingNumber());
		}
		List<Long> orderItemIds = List.copyOf(courierCodesByItemId.keySet());
		lockOrders(orderItemIds);
		List<OrderItem> items = orderItemRepository.findAllById(orderItemIds);
		LocalDateTime now = LocalDateTime.now(clock);

		Set<Long> changedOrderIds = new LinkedHashSet<>();
		int processed = 0;
		for (OrderItem item : items) {
			CourierCode courierCode = courierCodesByItemId.get(item.getId());
			String trackingNumber = trackingNumbersByItemId.get(item.getId());
			if (item.startShipping(courierCode, trackingNumber, now)) {
				processed++;
				changedOrderIds.add(item.getOrder().getId());
			}
		}
		syncAndAudit(adminId, changedOrderIds, OrderItemStatus.SHIPPING, OrderStatus.SHIPPED,
				"PAID/PREPARING->SHIPPING");
		return toResult(processed, orderItemIds.size());
	}

	@Transactional
	public AdminOrderItemBulkResultResponse completeDelivery(Long adminId, AdminOrderItemDeliverRequest request) {
		List<Long> orderItemIds = request.orderItemIds();
		lockOrders(orderItemIds);
		List<OrderItem> items = orderItemRepository.findAllById(orderItemIds);
		LocalDateTime now = LocalDateTime.now(clock);

		Set<Long> changedOrderIds = new LinkedHashSet<>();
		int processed = 0;
		for (OrderItem item : items) {
			if (item.completeDelivery(now)) {
				processed++;
				changedOrderIds.add(item.getOrder().getId());
			}
		}
		syncAndAudit(adminId, changedOrderIds, OrderItemStatus.DELIVERED, OrderStatus.DELIVERED,
				"SHIPPING->DELIVERED");
		return toResult(processed, orderItemIds.size());
	}

	/** 대상 상품주문이 속한 주문 id 를 오름차순으로 정렬해 순서대로 잠근다(교차 잠금 순서로 인한 데드락 방지). */
	private void lockOrders(List<Long> orderItemIds) {
		orderItemRepository.findDistinctOrderIdsByIdIn(orderItemIds).stream()
				.sorted()
				.forEach(orderId -> orderRepository.findByIdForUpdate(orderId)
						.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND)));
	}

	private void syncAndAudit(Long adminId, Set<Long> changedOrderIds, OrderItemStatus targetItemStatus,
			OrderStatus targetOrderStatus, String detail) {
		for (Long orderId : changedOrderIds) {
			orderStatusAligner.alignIfAllItemsMatch(orderId, targetItemStatus, targetOrderStatus);
			adminAuditLogService.record(adminId, AdminAuditAction.ORDER_STATUS_CHANGE, AdminAuditTargetType.ORDER,
					orderId, detail);
		}
	}

	private CourierCode parseCourierCode(String value) {
		try {
			return CourierCode.valueOf(value);
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}

	private AdminOrderItemBulkResultResponse toResult(int processed, int requested) {
		return new AdminOrderItemBulkResultResponse(processed, requested - processed);
	}
}
