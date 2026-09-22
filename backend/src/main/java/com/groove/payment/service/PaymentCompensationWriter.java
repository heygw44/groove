package com.groove.payment.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.config.PaymentReconcileProperties;
import com.groove.payment.entity.PaymentCompensation;
import com.groove.payment.repository.PaymentCompensationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** payment 행이 없는 보상 취소 대기 큐의 짧은 트랜잭션 쓰기. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompensationWriter {

	private final PaymentCompensationRepository repository;
	private final OrderRepository orderRepository;
	private final PaymentReconcileProperties properties;

	/** 같은 paymentKey 행이 이미 있으면 재사용하고 새로 만들지 않는다(멱등). */
	@Transactional
	public void enqueue(String paymentKey, Long orderId, String tossOrderId, LocalDateTime approvedAt,
			String reason) {
		if (repository.findByPaymentKey(paymentKey).isPresent()) {
			return;
		}
		Order order = orderRepository.getReferenceById(orderId);
		repository.save(PaymentCompensation.pending(paymentKey, order, tossOrderId, approvedAt, reason));
	}

	@Transactional
	public void complete(String paymentKey, LocalDateTime canceledAt) {
		getByPaymentKey(paymentKey).markCanceled(canceledAt);
	}

	/** 재시도 횟수를 올리고, 상한에 닿으면 더 재시도하지 않도록 수동 확인으로 넘긴다. */
	@Transactional
	public void fail(String paymentKey, String error) {
		PaymentCompensation compensation = getByPaymentKey(paymentKey);
		compensation.recordFailure(error);
		if (compensation.getAttempts() >= properties.maxAttempts()) {
			log.error("결제 보상 대기 상한 도달, 수동 확인 필요: paymentKey={}", paymentKey);
			compensation.markManualReview();
		}
	}

	/** 재시도해도 결과가 바뀌지 않는 명확한 거절이라 상한을 기다리지 않고 즉시 수동 확인으로 넘긴다. */
	@Transactional
	public void reviewManually(String paymentKey, String detail) {
		PaymentCompensation compensation = getByPaymentKey(paymentKey);
		compensation.recordFailure(detail);
		compensation.markManualReview();
	}

	private PaymentCompensation getByPaymentKey(String paymentKey) {
		return repository.findByPaymentKey(paymentKey)
				.orElseThrow(() -> new IllegalStateException("보상 대기 행을 찾을 수 없습니다: paymentKey=" + paymentKey));
	}
}
