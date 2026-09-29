package com.groove.order.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimType;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemActionPolicy;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.client.dto.RefundAccountInfo;

import lombok.RequiredArgsConstructor;

/**
 * 취소·반품 클레임의 DB 쓰기. 동시성은 기존 관례를 따른다 - {@code orders FOR UPDATE} 로 주문을 먼저 잠근 뒤
 * 상품 행을 다룬다. 승인·완료(환불이 성공한 뒤 클레임을 종결하는 지점)는 {@link OrderClaimFinalizeService} 가
 * 맡는다 - 즉시 반영 경로와 대사 경로가 같은 종결 로직을 타야 하기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class OrderClaimWriter {

	private final OrderRepository orderRepository;
	private final OrderClaimRepository orderClaimRepository;
	private final Clock clock;

	/**
	 * 구매자 취소 요청. {@code PAID} 면 즉시 취소 대상(자동 승인), {@code PREPARING} 이면 관리자 승인을
	 * 기다리는 요청이 된다. 발주확인과 겹치면(잠근 뒤 다시 읽은 상태가 PREPARING) 요청 경로로 자동 전환된다.
	 */
	@Transactional
	public OrderClaimRequestResult requestCancel(Long memberId, Long orderId, Long itemId, String reason,
			RefundAccountInfo refundAccount) {
		Order order = lockOwnedOrder(orderId, memberId);
		OrderItem item = findItem(order, itemId);
		validateNoActiveClaim(item);
		OrderItemStatus status = item.getStatus();
		if (status != OrderItemStatus.PAID && status != OrderItemStatus.PREPARING) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		LocalDateTime now = LocalDateTime.now(clock);
		OrderClaim claim = orderClaimRepository.save(OrderClaim.requestCancel(item, reason, refundAccount, now));
		item.markClaimRequested(OrderItemClaimStatus.CANCEL_REQUEST);
		boolean immediate = status == OrderItemStatus.PAID;
		return new OrderClaimRequestResult(claim.getId(), item.getId(), order.getId(), item.getRefundableAmount(),
				immediate);
	}

	/** 반품 요청. 배송완료 후 {@link OrderItemActionPolicy#RETURN_PERIOD_DAYS}일 이내이고 구매확정 전이어야 한다(D7). */
	@Transactional
	public OrderClaimRequestResult requestReturn(Long memberId, Long orderId, Long itemId, String reason) {
		Order order = lockOwnedOrder(orderId, memberId);
		OrderItem item = findItem(order, itemId);
		validateNoActiveClaim(item);
		if (item.getStatus() != OrderItemStatus.DELIVERED) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		LocalDateTime now = LocalDateTime.now(clock);
		if (item.getDeliveredAt() == null
				|| now.isAfter(item.getDeliveredAt().plusDays(OrderItemActionPolicy.RETURN_PERIOD_DAYS))) {
			throw new BusinessException(ErrorCode.ORDER_RETURN_PERIOD_EXPIRED);
		}
		OrderClaim claim = orderClaimRepository.save(OrderClaim.requestReturn(item, reason, now));
		item.markClaimRequested(OrderItemClaimStatus.RETURN_REQUEST);
		return new OrderClaimRequestResult(claim.getId(), item.getId(), order.getId(), item.getRefundableAmount(),
				false);
	}

	/** 구매자 본인의 {@code REQUESTED} 클레임 철회. 상품주문은 클레임 이전 상태로 돌아간다(claim_status 비움). */
	@Transactional
	public Long withdraw(Long memberId, Long claimId) {
		OrderClaim claim = orderClaimRepository.findByIdAndMemberId(claimId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND));
		OrderItem item = claim.getOrderItem();
		lockOrderOf(item);
		claim.withdraw(LocalDateTime.now(clock));
		item.clearClaim();
		return item.getId();
	}

	/**
	 * 관리자 판매취소. 구매자 요청 없이 관리자가 직접 즉시 취소를 시작한다({@code PAID}·{@code PREPARING} 모두
	 * 즉시 대상) - 승인 대기 없이 곧바로 환불을 시도하므로 항상 {@code immediate=true}다.
	 */
	@Transactional
	public OrderClaimRequestResult requestAdminCancel(Long orderId, Long itemId, String reason) {
		orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Order order = orderRepository.findWithItemsById(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		OrderItem item = findItem(order, itemId);
		validateNoActiveClaim(item);
		OrderItemStatus status = item.getStatus();
		if (status != OrderItemStatus.PAID && status != OrderItemStatus.PREPARING) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		LocalDateTime now = LocalDateTime.now(clock);
		OrderClaim claim = orderClaimRepository.save(OrderClaim.requestCancel(item, reason, null, now));
		item.markClaimRequested(OrderItemClaimStatus.CANCEL_REQUEST);
		return new OrderClaimRequestResult(claim.getId(), item.getId(), order.getId(), item.getRefundableAmount(),
				true);
	}

	/** 관리자, 반품 수거 완료 시 고른 재입고 여부를 먼저 기록한다(환불 결과불명 대비). */
	@Transactional
	public OrderClaim chooseRestock(Long claimId, Boolean restock) {
		OrderClaim claim = findClaim(claimId);
		lockOrderOf(claim.getOrderItem());
		claim.chooseRestock(restock);
		return claim;
	}

	/** 관리자 거부. 상품주문은 원래 단계로 되돌아가고 claim_status 는 거부 표시로 남는다. */
	@Transactional
	public OrderClaim reject(Long claimId, String rejectReason) {
		OrderClaim claim = findClaim(claimId);
		OrderItem item = claim.getOrderItem();
		lockOrderOf(item);
		claim.reject(rejectReason, LocalDateTime.now(clock));
		item.markClaimRejected(
				claim.getType() == OrderClaimType.CANCEL ? OrderItemClaimStatus.CANCEL_REJECT
						: OrderItemClaimStatus.RETURN_REJECT);
		return claim;
	}

	/** 관리자, 반품 수거 시작. */
	@Transactional
	public OrderClaim startCollecting(Long claimId) {
		OrderClaim claim = findClaim(claimId);
		OrderItem item = claim.getOrderItem();
		lockOrderOf(item);
		claim.startCollecting();
		item.markCollecting();
		return claim;
	}

	@Transactional(readOnly = true)
	public OrderClaim findClaim(Long claimId) {
		return orderClaimRepository.findWithOrderItemById(claimId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND));
	}

	private void validateNoActiveClaim(OrderItem item) {
		if (item.isClaimInProgress()) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_IN_PROGRESS);
		}
	}

	private Order lockOwnedOrder(Long orderId, Long memberId) {
		Order lockedOrder = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		if (!lockedOrder.getMember().getId().equals(memberId)) {
			throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
		}
		return orderRepository.findWithItemsByIdAndMemberId(orderId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}

	private void lockOrderOf(OrderItem item) {
		Long orderId = item.getOrder().getId();
		orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}

	private OrderItem findItem(Order order, Long itemId) {
		return order.getItems().stream()
				.filter(candidate -> candidate.getId().equals(itemId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}
}
