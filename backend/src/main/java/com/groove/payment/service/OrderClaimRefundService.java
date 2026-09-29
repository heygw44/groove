package com.groove.payment.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.service.OrderClaimFinalizeService;
import com.groove.order.service.OrderClaimRefundHook;
import com.groove.payment.client.dto.RefundAccountInfo;
import com.groove.payment.entity.Payment;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/** {@link OrderClaimRefundHook} 구현. {@link PaymentRefundService} 로 부분환불을 시도하고 결과를 클레임에 반영한다. */
@Service
@RequiredArgsConstructor
public class OrderClaimRefundService implements OrderClaimRefundHook {

	private final PaymentRepository paymentRepository;
	private final PaymentRefundService paymentRefundService;
	private final OrderClaimFinalizeService orderClaimFinalizeService;

	@Override
	public void refund(Long orderId, Long claimId, BigDecimal amount, String reason,
			RefundAccountInfo refundAccount) {
		Payment payment = paymentRepository.findByOrderId(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		PaymentRefundResult result;
		try {
			result = paymentRefundService.refund(payment.getId(), amount, reason, refundAccount, claimId);
		} catch (BusinessException ex) {
			if (ex.getErrorCode() == ErrorCode.PAYMENT_RESULT_UNKNOWN) {
				return;
			}
			orderClaimFinalizeService.applyRefundFailed(claimId);
			throw ex;
		}
		if (result.status() == PaymentRefundStatus.DONE) {
			orderClaimFinalizeService.applyRefundDone(claimId, null);
		}
	}
}
