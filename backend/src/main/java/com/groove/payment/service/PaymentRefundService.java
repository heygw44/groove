package com.groove.payment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.RefundAccountInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 부분취소를 포함한 결제 환불 진입점. 상품 단위 취소·반품 클레임 승인({@code com.groove.order.service.OrderClaimService})이
 * 이 메서드를 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

	private final PaymentRefundWriter writer;
	private final PaymentClient paymentClient;
	private final Clock clock;

	public PaymentRefundResult refund(Long paymentId, BigDecimal amount, String reason,
			RefundAccountInfo refundAccount) {
		return resolve(writer.requestRefund(paymentId, amount, reason, refundAccount));
	}

	/** {@code orderClaimId} 가 있으면 이 취소 건이 어느 클레임 승인으로 시작됐는지 payment_cancel 에 같이 남긴다. */
	public PaymentRefundResult refund(Long paymentId, BigDecimal amount, String reason,
			RefundAccountInfo refundAccount, Long orderClaimId) {
		return resolve(writer.requestRefund(paymentId, amount, reason, refundAccount, orderClaimId));
	}

	private PaymentRefundResult resolve(PaymentRefundRequest request) {
		Long paymentId = request.paymentId();
		PaymentCancelCommand command = PaymentCancelCommand.of(request.paymentKey(), request.reason(),
				request.cancelAmount(), request.idempotencyKey(), request.refundAccount());
		PaymentCancelResult tossResult;
		try {
			tossResult = paymentClient.cancel(command);
		} catch (BusinessException ex) {
			if (ex.getErrorCode() == ErrorCode.PAYMENT_RESULT_UNKNOWN) {
				log.warn("토스 부분취소 결과 불명: paymentId={}, paymentCancelId={}", paymentId, request.paymentCancelId());
				throw ex;
			}
			safeFail(request);
			throw ex;
		}

		LocalDateTime canceledAt = tossResult.canceledAt() != null ? tossResult.canceledAt()
				: LocalDateTime.now(clock);
		try {
			writer.completeRefund(paymentId, request.paymentCancelId(), request.cancelAmount(),
					tossResult.transactionKey(), canceledAt);
		} catch (RuntimeException ex) {
			// 토스 취소 자체는 성공했으므로 실패로 보고하지 않는다. payment_cancel 행이 REQUESTED 로 남아
			// 이후 대사(PaymentCancelRetrier, 후속 과제)가 같은 idempotencyKey 로 이어받는다.
			log.error("토스 부분취소 후 반영 실패, 대사로 수렴: paymentId={}, paymentCancelId={}", paymentId,
					request.paymentCancelId(), ex);
			return new PaymentRefundResult(PaymentRefundStatus.IN_PROGRESS, request.paymentCancelId(), null);
		}
		return new PaymentRefundResult(PaymentRefundStatus.DONE, request.paymentCancelId(), request.cancelAmount());
	}

	private void safeFail(PaymentRefundRequest request) {
		try {
			writer.failRefund(request.paymentCancelId());
		} catch (RuntimeException failWriteFailure) {
			log.error("토스 부분취소 거절 기록 실패: paymentId={}, paymentCancelId={}", request.paymentId(),
					request.paymentCancelId(), failWriteFailure);
		}
	}
}
