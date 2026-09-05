package com.groove.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.Order;
import com.groove.order.service.PaymentCancelHook;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/** 결제 취소 훅 구현체. {@link com.groove.order.service.OrderService}/{@code AdminOrderService} 를 의존하지 않는다. */
@Service
@RequiredArgsConstructor
public class PaymentCancelService implements PaymentCancelHook {

	private static final String DEFAULT_CANCEL_REASON = "주문 취소";

	private final PaymentRepository paymentRepository;
	private final PaymentClient paymentClient;
	private final Clock clock;

	/**
	 * 토스 호출을 트랜잭션 안에서 해야 취소가 실패했을 때 주문·재고·쿠폰·한정반 선점 복구까지 전부 롤백된다.
	 * 토스 취소가 성공한 뒤 이 트랜잭션 커밋이 실패하면 결제 대행사와 DB 상태가 어긋나므로 수동 정산 대상이다.
	 * 부분 취소는 범위 밖이라 전액만 취소한다. {@code PaymentClient.cancel} 에 금액 인자를 두지 않은 이유다.
	 */
	@Transactional
	@Override
	public Long onPaidOrderCanceled(Order order) {
		Payment payment = paymentRepository.findByOrderId(order.getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() != PaymentStatus.DONE) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		String reason = resolveReason(order.getCancelReason());
		PaymentCancelResult result = paymentClient.cancel(payment.getPaymentKey(), reason);
		LocalDateTime canceledAt = result.canceledAt() != null ? result.canceledAt() : LocalDateTime.now(clock);
		payment.cancel(canceledAt);
		return payment.getId();
	}

	private String resolveReason(String orderCancelReason) {
		if (orderCancelReason == null || orderCancelReason.isBlank()) {
			return DEFAULT_CANCEL_REASON;
		}
		return orderCancelReason;
	}
}
