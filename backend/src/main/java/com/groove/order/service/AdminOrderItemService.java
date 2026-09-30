package com.groove.order.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
import com.groove.order.mapper.OrderQueryMapper;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 상품주문 목록 조회와 발주확인·발송처리·배송완료 일괄 처리. 대상이 아닌 상품주문(상태가 다르거나 진행 중
 * 클레임이 있음)은 예외로 막지 않고 건너뛰어 처리/건너뜀 건수로만 알려준다 - 관리자가 여러 상태가 섞인 상품주문을
 * 한 번에 선택해도 일부만 유효하면 그만큼은 처리되게 하기 위함이다.
 *
 * <p>일괄 처리는 대상 주문을 id 순으로 잠근 뒤 상품주문을 처음 읽는다. READ COMMITTED 인 이유: 락을 기다리는 동안
 * 구매자 취소가 커밋한 클레임 표시를 봐야 한다. REPEATABLE READ 면 락 이전 스냅샷으로 판단해 취소 요청된 상품을
 * 발송하고 claim_status 까지 덮어쓴다. 전액취소가 진행 중(결제 CANCEL_REQUESTED)인 주문의 상품도 발주확인·발송하지
 * 않는다 - 대사가 취소를 확정하면 발송된 상품까지 취소완료가 된다.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminOrderItemService {

	private final OrderQueryMapper orderQueryMapper;
	private final OrderItemRepository orderItemRepository;
	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
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

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public AdminOrderItemBulkResultResponse confirmPreparing(Long adminId, AdminOrderItemConfirmRequest request) {
		List<Long> orderItemIds = request.orderItemIds();
		Set<Long> cancelRequestedOrderIds = lockOrders(orderItemIds);
		List<OrderItem> items = orderItemRepository.findAllById(orderItemIds);
		LocalDateTime now = LocalDateTime.now(clock);

		Set<Long> changedOrderIds = new LinkedHashSet<>();
		int processed = 0;
		for (OrderItem item : items) {
			if (cancelRequestedOrderIds.contains(item.getOrder().getId())) {
				continue;
			}
			if (item.confirmPreparing(now)) {
				processed++;
				changedOrderIds.add(item.getOrder().getId());
			}
		}
		auditOrders(adminId, changedOrderIds, "PAID->PREPARING");
		return toResult(processed, orderItemIds.size());
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public AdminOrderItemBulkResultResponse startShipping(Long adminId, AdminOrderItemShipRequest request) {
		// courierCode 파싱은 잠금 전에 전부 끝낸다 - 하나라도 잘못되면 아무 것도 잠그거나 바꾸지 않고 즉시 400.
		Map<Long, CourierCode> courierCodesByItemId = new LinkedHashMap<>();
		Map<Long, String> trackingNumbersByItemId = new LinkedHashMap<>();
		for (AdminOrderItemShipRequest.ShipItem shipItem : request.items()) {
			courierCodesByItemId.put(shipItem.orderItemId(), parseCourierCode(shipItem.courierCode()));
			trackingNumbersByItemId.put(shipItem.orderItemId(), shipItem.trackingNumber());
		}
		List<Long> orderItemIds = List.copyOf(courierCodesByItemId.keySet());
		Set<Long> cancelRequestedOrderIds = lockOrders(orderItemIds);
		List<OrderItem> items = orderItemRepository.findAllById(orderItemIds);
		LocalDateTime now = LocalDateTime.now(clock);

		Set<Long> changedOrderIds = new LinkedHashSet<>();
		int processed = 0;
		for (OrderItem item : items) {
			if (cancelRequestedOrderIds.contains(item.getOrder().getId())) {
				continue;
			}
			CourierCode courierCode = courierCodesByItemId.get(item.getId());
			String trackingNumber = trackingNumbersByItemId.get(item.getId());
			if (item.startShipping(courierCode, trackingNumber, now)) {
				processed++;
				changedOrderIds.add(item.getOrder().getId());
			}
		}
		auditOrders(adminId, changedOrderIds, "PAID/PREPARING->SHIPPING");
		return toResult(processed, orderItemIds.size());
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
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
		auditOrders(adminId, changedOrderIds, "SHIPPING->DELIVERED");
		return toResult(processed, orderItemIds.size());
	}

	/**
	 * 대상 상품주문이 속한 주문 id 를 오름차순으로 정렬해 순서대로 잠근다(교차 잠금 순서로 인한 데드락 방지). 잠근
	 * 뒤 전액취소가 진행 중인 주문 id 를 돌려준다 - 전액취소 요청도 같은 주문 락 안에서 결제를 CANCEL_REQUESTED 로
	 * 옮기므로 락 이후의 조회가 확정값이다.
	 */
	private Set<Long> lockOrders(List<Long> orderItemIds) {
		List<Long> orderIds = orderItemRepository.findDistinctOrderIdsByIdIn(orderItemIds).stream()
				.sorted()
				.toList();
		orderIds.forEach(orderId -> orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND)));
		if (orderIds.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(paymentRepository.findOrderIdsByOrderIdInAndStatus(orderIds,
				PaymentStatus.CANCEL_REQUESTED));
	}

	private void auditOrders(Long adminId, Set<Long> changedOrderIds, String detail) {
		for (Long orderId : changedOrderIds) {
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
