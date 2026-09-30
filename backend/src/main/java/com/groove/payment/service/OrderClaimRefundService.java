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

/**
 * {@link OrderClaimRefundHook} 구현. {@link PaymentRefundService} 로 부분환불을 시도하고 결과를 클레임에 반영한다.
 * 성공 반영(클레임 마무리)은 {@link PaymentRefundWriter#completeRefund} 가 환불 기록과 같은 트랜잭션에서 한다.
 */
@Service
@RequiredArgsConstructor
public class OrderClaimRefundService implements OrderClaimRefundHook {

	private final PaymentRepository paymentRepository;
	private final PaymentRefundService paymentRefundService;
	private final OrderClaimFinalizeService orderClaimFinalizeService;

	/**
	 * 클레임을 거부로 되돌리는 건 토스가 명시적으로 거절한 경우({@code PAYMENT_CANCEL_FAILED})뿐이다. 다른 환불이
	 * 진행 중이라거나 환불계좌가 없다는 등 요청 기록 전 검증 실패는 클레임과 무관한 사정이라 그대로 둔 채 던진다.
	 */
	@Override
	public void refund(Long orderId, Long claimId, BigDecimal amount, String reason,
			RefundAccountInfo refundAccount) {
		Payment payment = paymentRepository.findByOrderId(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		try {
			paymentRefundService.refund(payment.getId(), amount, reason, refundAccount, claimId);
		} catch (BusinessException ex) {
			if (ex.getErrorCode() == ErrorCode.PAYMENT_RESULT_UNKNOWN) {
				return;
			}
			if (ex.getErrorCode() == ErrorCode.PAYMENT_CANCEL_FAILED) {
				orderClaimFinalizeService.applyRefundFailed(claimId);
			}
			throw ex;
		}
	}
}
