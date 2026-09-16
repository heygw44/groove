package com.groove.payment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.dto.PaymentConfirmResponse;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.service.ProductSalesStatsUpdater;

import lombok.RequiredArgsConstructor;

/**
 * 결제 승인의 DB 반영. 토스 호출은 {@link PaymentConfirmService} 가 트랜잭션 밖에서 맡고, 이 클래스는 쓰기만 한다.
 * fail/markUnknown 의 버전 충돌(ObjectOptimisticLockingFailureException)은 커밋 시점에 나므로 호출자가 처리한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentConfirmWriter {

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final ProductSalesStatsUpdater productSalesStatsUpdater;
	private final Clock clock;

	@Transactional
	public ConfirmPreparation prepare(Long memberId, PaymentConfirmRequest request) {
		Order order = orderRepository.findByOrderNumberForUpdate(request.orderId())
				.filter(o -> o.getMember().getId().equals(memberId))
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

		Optional<Payment> existing = paymentRepository.findByOrderId(order.getId());
		if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.DONE) {
			Payment done = existing.get();
			if (request.paymentKey().equals(done.getPaymentKey())) {
				return new ConfirmPreparation(done.getId(), order.getId(), order.getOrderNumber(),
						order.getFinalAmount(), Optional.of(PaymentConfirmResponse.from(done)));
			}
			throw new BusinessException(ErrorCode.PAYMENT_ALREADY_DONE);
		}

		paymentRepository.findByPaymentKey(request.paymentKey())
				.filter(payment -> !payment.getOrder().getId().equals(order.getId()))
				.ifPresent(payment -> {
					throw new BusinessException(ErrorCode.PAYMENT_KEY_MISMATCH);
				});

		if (order.getStatus() != OrderStatus.PENDING) {
			throw new BusinessException(order.getStatus() == OrderStatus.PAID
					? ErrorCode.PAYMENT_ALREADY_DONE
					: ErrorCode.ORDER_INVALID_STATUS);
		}
		if (BigDecimal.valueOf(request.amount()).compareTo(order.getFinalAmount()) != 0) {
			throw new BusinessException(ErrorCode.ORDER_AMOUNT_MISMATCH);
		}
		if (order.isExpired(LocalDateTime.now(clock))) {
			throw new BusinessException(ErrorCode.ORDER_EXPIRED);
		}

		Payment payment = existing.orElseGet(() -> paymentRepository.save(Payment.ready(order)));
		if (payment.getStatus() == PaymentStatus.FAILED) {
			payment.retry();
		}
		return new ConfirmPreparation(payment.getId(), order.getId(), order.getOrderNumber(), order.getFinalAmount(),
				Optional.empty());
	}

	/**
	 * 주문 락을 결제 조회보다 먼저 잡는다. MySQL RR 의 일관 읽기 스냅샷은 트랜잭션의 첫 비잠금 읽기에서
	 * 고정되므로, 락(잠금 읽기)을 먼저 거쳐야 뒤이은 결제 조회가 그사이 먼저 커밋된 승인을 놓치지 않는다.
	 */
	@Transactional
	public PaymentConfirmResponse approve(Long orderId, Long paymentId, String paymentKey,
			PaymentConfirmResult result) {
		Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (!payment.getOrder().getId().equals(orderId)) {
			throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
		}
		if (payment.getStatus() == PaymentStatus.DONE && paymentKey.equals(payment.getPaymentKey())) {
			return PaymentConfirmResponse.from(payment);
		}

		LocalDateTime approvedAt = result.approvedAt() != null ? result.approvedAt() : LocalDateTime.now(clock);
		payment.approve(paymentKey, result.method(), approvedAt);
		order.markPaid();

		try {
			paymentRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.PAYMENT_KEY_MISMATCH);
		}

		// 재고는 여기서 건드리지 않는다. 차감과 OUT 이력은 주문 생성 시 이미 기록됐고, 승인 확정용
		// StockChangeType 을 새로 추가하면 운영 DB 의 Hibernate enum CHECK 제약을 갱신해야 한다.

		productSalesStatsUpdater.refreshFor(order);
		return PaymentConfirmResponse.from(payment);
	}

	@Transactional
	public void fail(Long paymentId, String reason) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() == PaymentStatus.DONE || payment.getStatus() == PaymentStatus.UNKNOWN) {
			return;
		}
		payment.fail(reason);
	}

	@Transactional
	public void markUnknown(Long paymentId, String reason) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() == PaymentStatus.DONE) {
			return;
		}
		payment.markUnknown(reason);
	}

	@Transactional
	public void markCompensated(Long paymentId, String paymentKey, LocalDateTime approvedAt, LocalDateTime canceledAt,
			String reason) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		payment.compensate(paymentKey, approvedAt, canceledAt, reason);
	}
}
