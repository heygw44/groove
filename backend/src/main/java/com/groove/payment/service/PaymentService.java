package com.groove.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.service.OrderService;
import com.groove.payment.dto.PaymentCancelRequest;
import com.groove.payment.dto.PaymentCancelResponse;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/** 결제 취소 진입점. 결제만 CANCELED 로 두고 주문을 PAID 로 남기면 정합성이 깨지므로 주문 취소에 위임한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final OrderService orderService;

	@Transactional
	public PaymentCancelResponse cancel(Long memberId, Long paymentId, PaymentCancelRequest request) {
		Payment payment = paymentRepository.findByIdAndOrderMemberId(paymentId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() != PaymentStatus.DONE) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		// 결제 취소 자체는 PaymentCancelHook(PaymentCancelService) 경로 하나로만 일어난다.
		orderService.cancel(memberId, payment.getOrder().getId(), new OrderCancelRequest(request.reason()));
		return PaymentCancelResponse.from(payment);
	}
}
