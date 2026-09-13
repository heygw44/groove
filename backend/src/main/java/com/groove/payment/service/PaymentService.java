package com.groove.payment.service;

import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.service.OrderCancelService;
import com.groove.payment.dto.PaymentCancelRequest;
import com.groove.payment.dto.PaymentCancelResponse;
import com.groove.payment.dto.PaymentCancelTarget;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/** 결제 취소 진입점. 결제만 CANCELED 로 두고 주문을 PAID 로 남기면 정합성이 깨지므로 주문 취소에 위임한다. */
@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final OrderCancelService orderCancelService;

	public PaymentCancelResponse cancel(Long memberId, Long paymentId, PaymentCancelRequest request) {
		PaymentCancelTarget target = paymentRepository.findCancelTarget(paymentId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (target.status() != PaymentStatus.DONE && target.status() != PaymentStatus.CANCEL_REQUESTED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		return PaymentCancelResponse.from(orderCancelService.cancel(memberId, target.orderId(),
				new OrderCancelRequest(request.reason())));
	}
}
