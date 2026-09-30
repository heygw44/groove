package com.groove.order.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimType;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * 부분환불의 최종 결과(성공·실패)를 클레임과 상품주문에 반영한다. 클레임 승인 직후 결과가 바로 확인되는 경우와,
 * 결과불명으로 남았다가 대사({@code PaymentCancelRetrier})가 나중에 확정하는 경우 모두 이 클래스를 거친다 -
 * 클레임 완료 로직이 한 곳에만 있어야 두 경로가 어긋나지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OrderClaimFinalizeService {

	private final OrderClaimRepository orderClaimRepository;
	private final OrderRepository orderRepository;
	private final OrderCancelRestorer restorer;
	private final Clock clock;

	/**
	 * 환불 요청 기록(payment_cancel INSERT) 직전에 주문을 잠그고 클레임이 아직 진행 중인지 다시 확인한다. 같은
	 * 트랜잭션에서 이 클레임의 환불 행까지 확인해야, 동시에 들어온 승인이나 승인과 철회·거부가 주문 락 하나로
	 * 직렬화된다. 호출자 트랜잭션은 READ COMMITTED 여야 락을 잡은 뒤 읽는 클레임·환불 행이 최신이다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockRefundableClaim(Long orderClaimId) {
		Long orderId = orderClaimRepository.findOrderIdById(orderClaimId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND));
		orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		OrderClaim claim = orderClaimRepository.findById(orderClaimId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND));
		if (!claim.isInProgress()) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
	}

	/**
	 * 토스 부분취소가 확정됐을 때 클레임을 종결하고 상품주문을 취소·반품으로 확정한다. 이미 종결된 클레임이면
	 * 아무 것도 하지 않는다(멱등 - 즉시 반영 경로와 대사 경로가 같은 클레임을 두 번 마무리하려 할 수 있다).
	 */
	@Transactional
	public void applyRefundDone(Long orderClaimId, LocalDateTime canceledAt) {
		OrderClaim claim = orderClaimRepository.findById(orderClaimId).orElse(null);
		if (claim == null || !claim.isInProgress()) {
			return;
		}
		Long orderId = claim.getOrderItem().getOrder().getId();
		Order order = lockAndLoadOrder(orderId);
		OrderItem item = findItem(order, claim.getOrderItem().getId());
		LocalDateTime now = canceledAt != null ? canceledAt : LocalDateTime.now(clock);
		boolean restock;
		if (claim.getType() == OrderClaimType.CANCEL) {
			claim.approve(now);
			item.completeCancelClaim(now);
			restock = true;
		} else {
			Boolean chosenRestock = claim.getRestock();
			claim.complete(now, chosenRestock);
			item.completeReturnClaim(now);
			restock = Boolean.TRUE.equals(chosenRestock);
		}
		restorer.restoreItems(order, List.of(item), restock, true);
	}

	/**
	 * 토스가 명시적으로 거절했거나(즉시 반영 경로) 대사가 미반영을 확인했을 때 클레임을 거부로 되돌린다.
	 * 상품주문의 이행 상태는 그대로 두고 클레임 파생 표시만 남긴다.
	 */
	@Transactional
	public void applyRefundFailed(Long orderClaimId) {
		OrderClaim claim = orderClaimRepository.findById(orderClaimId).orElse(null);
		if (claim == null || !claim.isInProgress()) {
			return;
		}
		Long orderId = claim.getOrderItem().getOrder().getId();
		Order order = lockAndLoadOrder(orderId);
		OrderItem item = findItem(order, claim.getOrderItem().getId());
		LocalDateTime now = LocalDateTime.now(clock);
		claim.reject("결제 취소 실패", now);
		item.markClaimRejected(
				claim.getType() == OrderClaimType.CANCEL ? OrderItemClaimStatus.CANCEL_REJECT
						: OrderItemClaimStatus.RETURN_REJECT);
	}

	private Order lockAndLoadOrder(Long orderId) {
		orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		return orderRepository.findWithItemsById(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}

	private OrderItem findItem(Order order, Long itemId) {
		return order.getItems().stream()
				.filter(candidate -> candidate.getId().equals(itemId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}
}
