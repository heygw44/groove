package com.groove.order.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.service.LimitedRelease;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderCancelWriter {

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final OrderCancelRestorer restorer;

	@Transactional(readOnly = true)
	public OrderCancelTarget findTarget(Long memberId, Long orderId) {
		Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		PaymentStatus paymentStatus = paymentRepository.findByOrderId(orderId)
				.map(payment -> payment.getStatus())
				.orElse(null);
		return new OrderCancelTarget(order.getStatus(), paymentStatus);
	}

	/**
	 * 결제 있는 주문의 취소 대상을 상품주문 단위로 미리 살핀다. 기존 전액취소 경로(결제를 한 번에 통째로
	 * 취소)는 상품주문이 전부 PAID 이고 클레임 이력이 전혀 없고(claim_status 가 모두 null) 결제가
	 * DONE·CANCEL_REQUESTED 일 때만 안전하다 - 상품 하나가 이미 취소·반품됐거나(PARTIAL_CANCELED),
	 * PREPARING 이 섞여 있으면 남은 상품 금액만큼만 취소해야 하므로 상품 단위 클레임 경로로 넘어가야 한다.
	 */
	@Transactional(readOnly = true)
	public OrderCancelPlan planCancel(Long memberId, Long orderId) {
		Order order = orderRepository.findWithItemsByIdAndMemberId(orderId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		List<Long> cancelableItemIds = order.getItems().stream()
				.filter(item -> isCancelable(item))
				.map(OrderItem::getId)
				.toList();
		boolean allPaidWithoutClaimHistory = order.getItems().stream()
				.allMatch(item -> item.getStatus() == OrderItemStatus.PAID && item.getClaimStatus() == null);
		PaymentStatus paymentStatus = paymentRepository.findByOrderId(orderId)
				.map(payment -> payment.getStatus())
				.orElse(null);
		boolean eligibleForFullCancel = allPaidWithoutClaimHistory
				&& (paymentStatus == PaymentStatus.DONE || paymentStatus == PaymentStatus.CANCEL_REQUESTED);
		return new OrderCancelPlan(cancelableItemIds, eligibleForFullCancel);
	}

	private boolean isCancelable(OrderItem item) {
		OrderItemStatus status = item.getStatus();
		return (status == OrderItemStatus.PAID || status == OrderItemStatus.PREPARING)
				&& !item.isClaimInProgress();
	}

	@Transactional
	public UnpaidCancelResult cancelUnpaid(Long memberId, Long orderId, String reason) {
		Order lockedOrder = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		if (!lockedOrder.getMember().getId().equals(memberId)) {
			throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
		}
		Order order = orderRepository.findWithItemsByIdAndMemberId(orderId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Optional<PaymentStatus> paymentStatus = paymentRepository.findByOrderId(orderId)
				.map(payment -> payment.getStatus());
		if (paymentStatus.filter(status -> status == PaymentStatus.DONE
				|| status == PaymentStatus.CANCEL_REQUESTED).isPresent()) {
			return UnpaidCancelResult.paymentCancelRequired();
		}
		order.cancel(reason);
		Long limitedDropId = restorer.restore(order, false).map(LimitedRelease::dropId).orElse(null);
		return UnpaidCancelResult.canceled(limitedDropId);
	}
}
