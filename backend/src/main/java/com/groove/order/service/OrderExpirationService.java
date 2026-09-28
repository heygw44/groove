package com.groove.order.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.service.PaymentLateResultApplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 기한이 지난 PENDING 주문 한 건을 취소하고 재고·쿠폰·한정반 선점을 되돌린다. 주문마다 별도 트랜잭션으로
 * 호출된다. 가상계좌는 토스 확인·폐쇄가 트랜잭션 밖에서 필요해 {@link OrderExpirationWriter} 를 두 단계로 나눠
 * 부른다 - 락·전이는 writer, 토스 호출은 여기서 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationService {

	private static final String EXPIRED_CLOSE_REASON = "입금기한 만료";

	private final OrderExpirationWriter writer;
	private final PaymentClient paymentClient;
	private final PaymentLateResultApplier paymentLateResultApplier;

	public boolean expire(Long orderId, LocalDateTime now) {
		Optional<OrderExpirationTarget> target = writer.checkExpirable(orderId, now);
		if (target.isEmpty()) {
			return false;
		}
		if (!target.get().waitingForDeposit()) {
			return true;
		}
		return expireVirtualAccount(target.get(), now);
	}

	private boolean expireVirtualAccount(OrderExpirationTarget target, LocalDateTime now) {
		PaymentLookupResult lookup;
		try {
			lookup = paymentClient.lookup(target.tossOrderId());
		} catch (RuntimeException ex) {
			log.warn("가상계좌 만료 처리 중 재조회 실패, 다음 스케줄에서 재시도: orderId={}", target.orderId(), ex);
			return false;
		}
		if (lookup.status() == PaymentLookupStatus.DONE) {
			PaymentReconcileCandidate candidate = new PaymentReconcileCandidate(target.paymentId(), target.orderId(),
					target.tossOrderId());
			paymentLateResultApplier.apply(candidate, lookup, "입금기한 만료 처리 중 입금 확인");
			return true;
		}
		if (lookup.status() == PaymentLookupStatus.WAITING_FOR_DEPOSIT) {
			try {
				paymentClient.cancel(target.paymentKey(), EXPIRED_CLOSE_REASON);
			} catch (RuntimeException ex) {
				log.warn("가상계좌 만료 처리 중 계좌 폐쇄 실패, 다음 스케줄에서 재시도: orderId={}", target.orderId(), ex);
				return false;
			}
		}
		return writer.finalizeVirtualAccountExpiry(target.orderId(), target.paymentId(), EXPIRED_CLOSE_REASON, now);
	}
}
