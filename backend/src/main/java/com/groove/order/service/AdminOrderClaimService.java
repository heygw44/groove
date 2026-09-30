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
import com.groove.order.dto.AdminOrderClaimCompleteRequest;
import com.groove.order.dto.AdminOrderClaimCountResponse;
import com.groove.order.dto.AdminOrderClaimRejectRequest;
import com.groove.order.dto.AdminOrderClaimSearchRequest;
import com.groove.order.dto.AdminOrderClaimSummaryResponse;
import com.groove.order.dto.AdminOrderItemCancelRequest;
import com.groove.order.dto.AdminOrderItemResponse;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimStatus;
import com.groove.order.entity.OrderClaimType;
import com.groove.order.entity.OrderItem;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.repository.OrderItemRepository;
import com.groove.product.repository.ProductImageRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자의 취소·반품 클레임 큐 조회·승인/거부/수거/완료와 판매취소. 환불 시도는 {@link OrderItemClaimService}
 * 와 마찬가지로 트랜잭션 밖에서 이뤄지므로 이 클래스도 클래스 레벨 트랜잭션을 걸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderClaimService {

	private final OrderClaimRepository orderClaimRepository;
	private final OrderClaimWriter writer;
	private final OrderClaimRefundHook refundHook;
	private final OrderItemRepository orderItemRepository;
	private final ProductImageRepository productImageRepository;
	private final AdminAuditLogService adminAuditLogService;

	@Transactional(readOnly = true)
	public PageResponse<AdminOrderClaimSummaryResponse> getList(AdminOrderClaimSearchRequest request) {
		return PageResponse.from(orderClaimRepository
				.search(request.type(), request.status(), request.toPageable())
				.map(AdminOrderClaimSummaryResponse::from));
	}

	@Transactional(readOnly = true)
	public AdminOrderClaimCountResponse getCounts() {
		return AdminOrderClaimCountResponse.from(orderClaimRepository.countByTypeAndStatus());
	}

	/** {@code CANCEL} 클레임 승인(REQUESTED → DONE). 즉시 취소와 같은 환불 경로를 탄다. */
	public AdminOrderItemResponse approve(Long adminId, Long claimId) {
		OrderClaim claim = writer.findClaim(claimId);
		if (claim.getType() != OrderClaimType.CANCEL || claim.getStatus() != OrderClaimStatus.REQUESTED) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		OrderItem item = claim.getOrderItem();
		Long orderId = item.getOrder().getId();
		Long itemId = item.getId();
		String productOrderNumber = item.getProductOrderNumber();
		refundHook.refund(orderId, claimId, item.getRefundableAmount(), claim.getReason(), claim.getRefundAccount());
		record(adminId, orderId, "클레임 승인(취소): " + productOrderNumber);
		return buildItemResponse(itemId);
	}

	/** 클레임 거부(REQUESTED·COLLECTING → REJECTED). 상품주문은 원래 단계로 되돌아간다. */
	public AdminOrderItemResponse reject(Long adminId, Long claimId, AdminOrderClaimRejectRequest request) {
		OrderClaim claim = writer.reject(claimId, request.rejectReason());
		Long orderId = claim.getOrderItem().getOrder().getId();
		record(adminId, orderId, "클레임 거부: " + claim.getOrderItem().getProductOrderNumber());
		return buildItemResponse(claim.getOrderItem().getId());
	}

	/** {@code RETURN} 클레임 수거 시작(REQUESTED → COLLECTING). */
	public AdminOrderItemResponse collect(Long adminId, Long claimId) {
		OrderClaim claim = writer.startCollecting(claimId);
		Long orderId = claim.getOrderItem().getOrder().getId();
		record(adminId, orderId, "반품 수거 시작: " + claim.getOrderItem().getProductOrderNumber());
		return buildItemResponse(claim.getOrderItem().getId());
	}

	/** {@code RETURN} 클레임 수거 완료(COLLECTING → DONE, restock 선택). */
	public AdminOrderItemResponse complete(Long adminId, Long claimId, AdminOrderClaimCompleteRequest request) {
		OrderClaim claim = writer.chooseRestock(claimId, request.restock());
		if (claim.getType() != OrderClaimType.RETURN || claim.getStatus() != OrderClaimStatus.COLLECTING) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		OrderItem item = claim.getOrderItem();
		Long orderId = item.getOrder().getId();
		Long itemId = item.getId();
		String productOrderNumber = item.getProductOrderNumber();
		refundHook.refund(orderId, claimId, item.getRefundableAmount(), claim.getReason(), claim.getRefundAccount());
		record(adminId, orderId, "반품 수거 완료: " + productOrderNumber);
		return buildItemResponse(itemId);
	}

	/**
	 * 판매취소(PAID·PREPARING → CANCELED, 즉시 부분환불). 구매자 요청 없이 관리자가 직접 시작한다.
	 * 경로 변수는 상품주문(order_item) id 라 주문 id 를 먼저 찾는다.
	 */
	public AdminOrderItemResponse cancelItemBySale(Long adminId, Long itemId, AdminOrderItemCancelRequest request) {
		String reason = request == null ? null : request.reason();
		Long orderId = orderItemRepository.findById(itemId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND))
				.getOrder().getId();
		OrderClaimRequestResult result = writer.requestAdminCancel(orderId, itemId, reason);
		refundHook.refund(orderId, result.claimId(), result.refundAmount(), reason, null);
		record(adminId, orderId, "판매취소: itemId=" + itemId);
		return buildItemResponse(itemId);
	}

	private void record(Long adminId, Long orderId, String detail) {
		adminAuditLogService.record(adminId, AdminAuditAction.ORDER_STATUS_CHANGE, AdminAuditTargetType.ORDER,
				orderId, detail);
	}

	private AdminOrderItemResponse buildItemResponse(Long itemId) {
		OrderItem item = orderItemRepository.findWithProductById(itemId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		String thumbnailUrl = productImageRepository
				.findAllByProductIdInAndSortOrder(List.of(item.getProduct().getId()), 0)
				.stream()
				.findFirst()
				.map(image -> image.getImageUrl())
				.orElse(null);
		return AdminOrderItemResponse.from(item, thumbnailUrl);
	}
}
