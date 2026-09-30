package com.groove.order.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimStatus;
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
 *
 * <p>이미 있는 클레임을 바꾸는 메서드는 주문 락을 먼저 잡고 클레임을 그 뒤에 읽는다. 격리 수준을 READ COMMITTED
 * 로 두는 이유: 락을 기다리는 동안 다른 트랜잭션이 커밋한 클레임 상태와 환불 행(payment_cancel)을 락 이후에
 * 봐야 한다. REPEATABLE READ 면 첫 조회 시점의 스냅샷을 계속 읽는다.</p>
 */
@Service
@RequiredArgsConstructor
public class OrderClaimWriter {

	private final OrderRepository orderRepository;
	private final OrderClaimRepository orderClaimRepository;
	private final OrderClaimRefundReader refundReader;
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

	/**
	 * 구매자 본인의 {@code REQUESTED} 클레임 철회. 상품주문은 클레임 이전 상태로 돌아간다(claim_status 비움).
	 * 토스에 나간 환불이 결과를 기다리는 중이면 막는다 - 철회된 뒤 환불이 확정되면 되돌릴 클레임이 없다.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Long withdraw(Long memberId, Long claimId) {
		Long orderId = orderClaimRepository.findOrderIdByIdAndMemberId(claimId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND));
		lockOrder(orderId);
		OrderClaim claim = findClaim(claimId);
		validateNoPendingRefund(claim);
		OrderItem item = claim.getOrderItem();
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

	/**
	 * 관리자 {@code CANCEL} 승인 전 확인. 환불 요청 기록({@code PaymentRefundWriter}) 트랜잭션에서 한 번 더
	 * 확인하지만, 결과불명으로 남은 클레임을 큐에서 다시 승인하는 경우는 여기서 먼저 409 로 돌려준다.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public OrderClaim lockApprovable(Long claimId) {
		OrderClaim claim = lockAndFindClaim(claimId);
		if (claim.getType() != OrderClaimType.CANCEL || claim.getStatus() != OrderClaimStatus.REQUESTED) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		validateNoPendingRefund(claim);
		return claim;
	}

	/**
	 * 관리자, 반품 수거 완료 시 고른 재입고 여부를 먼저 기록한다(환불 결과불명 대비). 환불이 이미 나간 뒤면 그
	 * 환불이 기록된 선택으로 마무리돼야 하므로 덮어쓰지 않는다.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public OrderClaim chooseRestock(Long claimId, Boolean restock) {
		OrderClaim claim = lockAndFindClaim(claimId);
		validateNoPendingRefund(claim);
		claim.chooseRestock(restock);
		return claim;
	}

	/** 관리자 거부. 상품주문은 원래 단계로 되돌아가고 claim_status 는 거부 표시로 남는다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public OrderClaim reject(Long claimId, String rejectReason) {
		OrderClaim claim = lockAndFindClaim(claimId);
		validateNoPendingRefund(claim);
		OrderItem item = claim.getOrderItem();
		claim.reject(rejectReason, LocalDateTime.now(clock));
		item.markClaimRejected(
				claim.getType() == OrderClaimType.CANCEL ? OrderItemClaimStatus.CANCEL_REJECT
						: OrderItemClaimStatus.RETURN_REJECT);
		return claim;
	}

	/**
	 * 즉시 취소(구매자 PAID 취소·관리자 판매취소)가 환불 요청 기록 전에 실패했을 때 방금 만든 클레임을 없던
	 * 일로 되돌린다. 이 클레임으로 나간 환불 행이 하나라도 있으면 토스 쪽 결과를 따라야 하므로 건드리지 않는다.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void discardUnstartedClaim(Long claimId) {
		OrderClaim claim = lockAndFindClaim(claimId);
		if (claim.getStatus() != OrderClaimStatus.REQUESTED || refundReader.hasAnyRefund(claimId)) {
			return;
		}
		claim.getOrderItem().clearClaim();
		orderClaimRepository.delete(claim);
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

	private void validateNoPendingRefund(OrderClaim claim) {
		if (refundReader.hasPendingRefund(claim.getId())) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);
		}
	}

	private OrderClaim lockAndFindClaim(Long claimId) {
		Long orderId = orderClaimRepository.findOrderIdById(claimId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND));
		lockOrder(orderId);
		return findClaim(claimId);
	}

	private void lockOrder(Long orderId) {
		orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
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
