package com.groove.order.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.limited.service.LimitedPurchaseWriter;
import com.groove.limited.service.LimitedRelease;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.Payment;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 결제 기한이 지난 PENDING 주문 한 건을 취소하고 재고·쿠폰·한정반 선점을 되돌린다. 주문마다 별도 트랜잭션으로 호출된다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationService {

	private final OrderRepository orderRepository;
	private final OrderStockService orderStockService;
	private final LimitedPurchaseWriter limitedPurchaseWriter;
	private final PaymentRepository paymentRepository;

	@Transactional
	public Optional<LimitedRelease> expire(Long orderId, LocalDateTime now) {
		// 락을 잡은 뒤 다시 확인해야 그 사이 결제·취소된 주문을 건너뛴다.
		Optional<Order> found = orderRepository.findByIdForUpdate(orderId);
		if (found.isEmpty() || !found.get().isExpired(now)) {
			log.debug("만료 대상에서 제외 orderId={}", orderId);
			return Optional.empty();
		}
		// prepare() 가 같은 주문 락 안에서 결제 행을 만들므로, 락 뒤 재확인이 대사 대기 중인 결제와의 경합을 닫는다.
		Optional<Payment> payment = paymentRepository.findByOrderId(orderId);
		if (payment.isPresent() && payment.get().getStatus().isUnresolved()) {
			log.debug("결제 대사 대기 중이라 만료를 건너뜀 orderId={}", orderId);
			return Optional.empty();
		}
		Order order = found.get();
		order.expire(now);
		orderStockService.restore(order);
		restoreCoupon(order);
		return limitedPurchaseWriter.revertByOrder(order.getId(), now);
	}

	private void restoreCoupon(Order order) {
		if (order.getMemberCoupon() != null && order.getMemberCoupon().isUsed()) {
			order.getMemberCoupon().restore();
		}
	}
}
