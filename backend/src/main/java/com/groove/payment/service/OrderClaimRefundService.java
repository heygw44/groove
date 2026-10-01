package com.groove.payment.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.service.OrderClaimFinalizeService;
import com.groove.order.service.OrderClaimRefundHook;
import com.groove.payment.client.dto.RefundAccountInfo;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentCancelStatus;
import com.groove.payment.repository.PaymentCancelRepository;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link OrderClaimRefundHook} 구현. {@link PaymentRefundService} 로 부분환불을 시도하고 결과를 클레임에 반영한다.
 * 성공 반영(클레임 마무리)은 {@link PaymentRefundWriter#completeRefund} 가 환불 기록과 같은 트랜잭션에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderClaimRefundService implements OrderClaimRefundHook {

	private final PaymentRepository paymentRepository;
	private final PaymentCancelRepository paymentCancelRepository;
	private final PaymentRefundService paymentRefundService;
	private final OrderClaimFinalizeService orderClaimFinalizeService;

	/**
	 * 클레임을 거부로 되돌리는 건 토스가 명시적으로 거절한 경우({@code PAYMENT_CANCEL_FAILED})뿐이다. 다른 환불이
	 * 진행 중이라거나 환불계좌가 없다는 등 요청 기록 전 검증 실패는 클레임과 무관한 사정이라 그대로 둔 채 던진다.
	 * 거절을 FAILED 로 기록하지 못해 취소 건이 REQUESTED 로 남았으면 거부하지 않는다. 대사가 같은 키로 다시 물어
	 * 성공으로 확정할 수도 있는데, 클레임을 먼저 닫으면 환불만 나가고 상품주문은 발송 대상으로 남는다.
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
				rejectClaimUnlessRefundPending(claimId);
			}
			throw ex;
		}
	}

	private void rejectClaimUnlessRefundPending(Long claimId) {
		if (paymentCancelRepository.existsByOrderClaimIdAndStatusIn(claimId, List.of(PaymentCancelStatus.REQUESTED))) {
			log.warn("토스 부분취소 거절을 기록하지 못해 클레임을 대사에 맡김: orderClaimId={}", claimId);
			return;
		}
		orderClaimFinalizeService.applyRefundFailed(claimId);
	}
}
