package com.groove.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.service.LimitedRelease;
import com.groove.order.service.PaidOrderCancelHook;
import com.groove.order.service.PaidOrderCancelResult;
import com.groove.order.service.PaidOrderCancelStatus;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaidOrderCancelService implements PaidOrderCancelHook {

	private final PaymentCancelWriter writer;
	private final PaymentClient paymentClient;
	private final Clock clock;

	@Override
	public PaidOrderCancelResult cancel(Long orderId, Long memberId, String reason) {
		CancelRequest request = writer.requestCancel(orderId, memberId, reason);
		if (request.alreadyRequested()) {
			return result(request, PaidOrderCancelStatus.IN_PROGRESS, true, null);
		}

		PaymentCancelResult tossResult;
		try {
			tossResult = paymentClient.cancel(request.paymentKey(), request.tossReason());
		} catch (BusinessException ex) {
			if (ex.getErrorCode() == ErrorCode.PAYMENT_RESULT_UNKNOWN) {
				log.warn("토스 취소 결과 불명: orderId={}, paymentId={}", orderId, request.paymentId());
				return result(request, PaidOrderCancelStatus.IN_PROGRESS, false, null);
			}
			safeRevert(request, ex);
			throw ex;
		}

		LocalDateTime canceledAt = tossResult.canceledAt() != null
				? tossResult.canceledAt()
				: LocalDateTime.now(clock);
		try {
			Long limitedDropId = writer.completeCancel(orderId, request.paymentId(), canceledAt)
					.map(LimitedRelease::dropId)
					.orElse(null);
			return result(request, PaidOrderCancelStatus.CANCELED, false, limitedDropId);
		} catch (RuntimeException ex) {
			log.error("토스 취소 후 복구 실패, 대사로 수렴: orderId={}, paymentId={}", orderId,
					request.paymentId(), ex);
			return result(request, PaidOrderCancelStatus.IN_PROGRESS, false, null);
		}
	}

	private void safeRevert(CancelRequest request, BusinessException cause) {
		try {
			writer.revertCancelRequest(request.orderId(), request.paymentId());
		} catch (RuntimeException revertFailure) {
			log.error("토스 취소 거절 후 요청 상태 복구 실패: orderId={}, paymentId={}", request.orderId(),
					request.paymentId(), revertFailure);
		}
	}

	private PaidOrderCancelResult result(CancelRequest request, PaidOrderCancelStatus status,
			boolean alreadyRequested, Long limitedDropId) {
		return new PaidOrderCancelResult(status, alreadyRequested, request.previousOrderStatus(), request.paymentId(),
				limitedDropId);
	}
}
