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
import com.groove.order.service.OrderPlacementService;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.client.dto.VirtualAccountInfo;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.dto.PaymentConfirmResponse;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentCancel;
import com.groove.payment.entity.PaymentCancelStatus;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentCancelRepository;
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

	private static final String COMPENSATION_IDEMPOTENCY_PREFIX = "cancel-";

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentCancelRepository paymentCancelRepository;
	private final ProductSalesStatsUpdater productSalesStatsUpdater;
	private final OrderPlacementService orderPlacementService;
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
		payment.approve(paymentKey, result.method(), approvedAt, result.easyPayProvider());
		order.markPaid();
		orderPlacementService.place(order, approvedAt);

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

	/**
	 * 가상계좌 발급 응답의 DB 반영. 주문은 PENDING 을 유지하고 입금기한으로 만료만 늘린다 - order.markPaid() 는
	 * 입금 확인 후 approve() 경로(웹훅·대사)에서만 호출된다.
	 */
	@Transactional
	public PaymentConfirmResponse issueVirtualAccount(Long orderId, Long paymentId, String paymentKey,
			PaymentConfirmResult result) {
		Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (!payment.getOrder().getId().equals(orderId)) {
			throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
		}
		if (payment.getStatus() == PaymentStatus.WAITING_FOR_DEPOSIT && paymentKey.equals(payment.getPaymentKey())) {
			return PaymentConfirmResponse.from(payment);
		}

		VirtualAccountInfo virtualAccount = result.virtualAccount();
		if (virtualAccount == null) {
			throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, "TOSS 가상계좌 정보가 없습니다.");
		}
		payment.issueVirtualAccount(paymentKey, result.method(), virtualAccount.bankCode(),
				virtualAccount.accountNumber(), virtualAccount.customerName(), virtualAccount.dueDate(),
				VirtualAccountSecretHasher.hash(virtualAccount.secret()));
		order.extendExpiry(virtualAccount.dueDate());
		order.awaitDeposit();
		orderPlacementService.place(order, LocalDateTime.now(clock));

		try {
			paymentRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.PAYMENT_KEY_MISMATCH);
		}
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

	/**
	 * 승인 직후 발견한 무효 주문의 보상 취소 반영. compensate() 는 CANCEL_REQUESTED 단계 없이 바로 CANCELED 로
	 * 가므로, 요청·완료를 한 번에 기록한 DONE 상태 payment_cancel 행을 이 자리에서 함께 남긴다 - 환불 통계가
	 * payment_cancel 하나만 보면 되게 하기 위해서다.
	 */
	@Transactional
	public void markCompensated(Long paymentId, String paymentKey, LocalDateTime approvedAt, LocalDateTime canceledAt,
			String reason, String transactionKey) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() == PaymentStatus.CANCELED) {
			return;
		}
		compensateWithCancelRecord(payment, paymentKey, approvedAt, canceledAt, reason, transactionKey);
	}

	/**
	 * 같은 멱등키 행이 이미 있으면(대사·이전 시도가 남긴 REQUESTED 행) 새로 만들지 않고 그 행을 완료한다 -
	 * payment_cancel.idempotency_key 유니크 충돌을 피한다. 호출자의 트랜잭션 안에서 실행된다.
	 */
	void compensateWithCancelRecord(Payment payment, String paymentKey, LocalDateTime approvedAt,
			LocalDateTime canceledAt, String reason, String transactionKey) {
		BigDecimal cancelAmount = payment.remainingAmount();
		payment.compensate(paymentKey, approvedAt, canceledAt, reason);
		String idempotencyKey = COMPENSATION_IDEMPOTENCY_PREFIX + payment.getPaymentKey();
		Optional<PaymentCancel> existing = paymentCancelRepository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			if (existing.get().getStatus() == PaymentCancelStatus.REQUESTED) {
				existing.get().complete(transactionKey, canceledAt);
			}
			return;
		}
		PaymentCancel paymentCancel = PaymentCancel.request(payment, idempotencyKey, cancelAmount, reason, canceledAt);
		paymentCancel.complete(transactionKey, canceledAt);
		paymentCancelRepository.save(paymentCancel);
	}
}
