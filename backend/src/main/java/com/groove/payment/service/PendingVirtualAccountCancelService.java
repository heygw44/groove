package com.groove.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.groove.limited.service.LimitedRelease;
import com.groove.order.service.PendingVirtualAccountCancelHook;
import com.groove.payment.client.PaymentClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 입금 전 가상계좌 주문 취소 진입점. 토스 호출을 DB 트랜잭션 밖에서 하기 위해 이 클래스는 트랜잭션을 걸지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingVirtualAccountCancelService implements PendingVirtualAccountCancelHook {

	private static final String DEFAULT_CANCEL_REASON = "주문 취소";

	private final PendingVirtualAccountCancelWriter writer;
	private final PaymentClient paymentClient;
	private final Clock clock;

	@Override
	public Long cancel(Long orderId, Long memberId, String reason) {
		String cancelReason = resolveReason(reason);
		PendingVirtualAccountCancelTarget target = writer.lock(orderId, memberId);
		try {
			paymentClient.cancel(target.paymentKey(), cancelReason);
		} catch (RuntimeException ex) {
			log.error("가상계좌 취소 중 계좌 폐쇄 실패: orderId={}, paymentId={}", orderId, target.paymentId(), ex);
			throw ex;
		}
		return writer.finalizeCancel(orderId, target.paymentId(), cancelReason, LocalDateTime.now(clock))
				.map(LimitedRelease::dropId)
				.orElse(null);
	}

	private String resolveReason(String reason) {
		return reason == null || reason.isBlank() ? DEFAULT_CANCEL_REASON : reason;
	}
}
