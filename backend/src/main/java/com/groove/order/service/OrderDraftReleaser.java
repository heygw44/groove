package com.groove.order.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.groove.order.entity.OrderSource;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 주문서 재제출(POST /orders) 전에, 같은 회원이 쥐고 있던 이전 미확정 PENDING 주문(한정반 제외)을
 * SUPERSEDED 로 풀어 재고·쿠폰을 돌려준다. 호출자({@link OrderService#create})의 트랜잭션에 참여한다 -
 * 멱등 실행기가 완료된 요청을 재생할 때는 supplier 자체를 다시 부르지 않으므로 해제도 다시 돌지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OrderDraftReleaser {

	private final OrderRepository orderRepository;
	private final OrderCancelRestorer orderCancelRestorer;
	private final PaymentRepository paymentRepository;

	@Transactional(propagation = Propagation.MANDATORY)
	public void releaseDrafts(Long memberId, LocalDateTime now) {
		List<Long> draftIds = orderRepository.findDraftIdsToSupersede(memberId, OrderStatus.PENDING,
				OrderSource.LIMITED, PaymentStatus.UNRESOLVED);
		for (Long orderId : draftIds) {
			release(orderId, now);
		}
	}

	// 조회와 잠금 사이 상태가 바뀔 수 있어(다른 요청이 먼저 풀었거나, 만료 스케줄러가 처리했거나, 결제 승인이 시작됨)
	// 잠근 뒤 다시 확인한다.
	private void release(Long orderId, LocalDateTime now) {
		orderRepository.findByIdForUpdate(orderId)
				.filter(order -> order.getStatus() == OrderStatus.PENDING && !order.isPlaced()
						&& order.getOrderSource() != OrderSource.LIMITED)
				.filter(order -> !hasUnresolvedPayment(orderId))
				.ifPresent(order -> {
					order.supersede(now);
					orderCancelRestorer.restore(order, false);
				});
	}

	private boolean hasUnresolvedPayment(Long orderId) {
		return paymentRepository.findByOrderId(orderId)
				.map(payment -> payment.getStatus().isUnresolved())
				.orElse(false);
	}
}
