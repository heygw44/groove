package com.groove.payment.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentTransaction;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 하루치 결제를 토스 거래 조회(GET /v1/transactions) 결과와 맞춰본다. 판단 자체는 짧은 트랜잭션으로 끝나는
 * {@link PaymentLateResultApplier} 에 위임하지만, 이 클래스는 그 사이사이 paymentClient.lookup(HTTP,
 * 최악 60초) 을 부르므로 클래스 전체를 트랜잭션으로 감쌀 수 없다 — 커넥션을 오래 붙들면 풀이 고갈된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

	private static final Set<PaymentStatus> LATE_APPLY_STATUSES = Set.of(PaymentStatus.READY, PaymentStatus.UNKNOWN,
			PaymentStatus.CANCEL_REQUESTED, PaymentStatus.FAILED);

	private static final Set<PaymentStatus> MISSING_FROM_TOSS_TARGET_STATUSES = Set.of(PaymentStatus.DONE,
			PaymentStatus.CANCELED, PaymentStatus.CANCEL_REQUESTED);

	private static final String CANCELED_TOSS_STATUS = "CANCELED";
	private static final String PARTIAL_CANCELED_TOSS_STATUS = "PARTIAL_CANCELED";

	private final PaymentRepository paymentRepository;
	private final PaymentClient paymentClient;
	private final PaymentLateResultApplier lateResultApplier;
	private final AlertNotifier alertNotifier;

	public PaymentSettlementReport reconcile(List<PaymentTransaction> transactions, LocalDateTime from,
			LocalDateTime to) {
		Map<String, PaymentTransaction> lastByOrderId = pickLastTransactionPerOrder(transactions);
		Counters counters = new Counters();

		for (Map.Entry<String, PaymentTransaction> entry : lastByOrderId.entrySet()) {
			try {
				reconcileTransaction(entry.getKey(), entry.getValue(), counters);
			} catch (RuntimeException ex) {
				counters.failed++;
				log.warn("결제 거래 대조 실패: orderId={}", entry.getKey(), ex);
			}
		}

		List<Payment> approvedPayments = paymentRepository.findByApprovedAtGreaterThanEqualAndApprovedAtLessThan(from,
				to);
		for (Payment payment : approvedPayments) {
			if (lastByOrderId.containsKey(payment.getTossOrderId())
					|| !MISSING_FROM_TOSS_TARGET_STATUSES.contains(payment.getStatus())) {
				continue;
			}
			try {
				reconcileMissingFromToss(payment, counters);
			} catch (RuntimeException ex) {
				counters.failed++;
				log.warn("결제 거래 대조 실패(토스 목록 누락): paymentId={}", payment.getId(), ex);
			}
		}

		log.info("결제 거래 대조 완료 transactions={} matched={} applied={} mismatched={} unknown={} failed={}",
				transactions.size(), counters.matched, counters.applied, counters.mismatched, counters.unknown,
				counters.failed);
		return new PaymentSettlementReport(transactions.size(), counters.matched, counters.applied,
				counters.mismatched, counters.unknown, counters.failed);
	}

	private void reconcileTransaction(String tossOrderId, PaymentTransaction transaction, Counters counters) {
		Optional<Payment> found = paymentRepository.findByTossOrderId(tossOrderId);
		// payment 행이 없는 운영 결제는 payment_compensation 대기 큐가 대사를 맡고, 로컬/운영이 같은 토스
		// 테스트 계정을 공유하는 경우도 있어 여기서는 경보 없이 넘어간다.
		if (found.isEmpty()) {
			counters.unknown++;
			return;
		}
		Payment payment = found.get();
		if (matches(payment.getStatus(), transaction.status())) {
			counters.matched++;
			return;
		}
		PaymentLookupResult lookup = paymentClient.lookup(tossOrderId);
		applyLateOrRecheck(payment, tossOrderId, lookup, counters);
	}

	private void reconcileMissingFromToss(Payment payment, Counters counters) {
		String tossOrderId = payment.getTossOrderId();
		PaymentLookupResult lookup = paymentClient.lookup(tossOrderId);
		applyLateOrRecheck(payment, tossOrderId, lookup, counters);
	}

	/**
	 * DB 가 아직 확정되지 않은 상태(READY/UNKNOWN/CANCEL_REQUESTED/FAILED)면 즉시 반영한다. DB 가 DONE/
	 * CANCELED 인데 여기까지 왔다면(대조표 불일치) 조회 결과로 다시 맞춰보고, 그래도 다르면 사람이 봐야 한다 —
	 * DB 가 이미 DONE 인 결제를 여기서 자동으로 취소하면 주문 취소·재고 복원까지 함께 굴러가야 하는데, 그건
	 * 창 경계로 생긴 가짜 불일치인지 실제 사고인지 구분 없이 되돌리는 셈이라 위험하다.
	 */
	private void applyLateOrRecheck(Payment payment, String tossOrderId, PaymentLookupResult lookup,
			Counters counters) {
		if (LATE_APPLY_STATUSES.contains(payment.getStatus())) {
			PaymentReconcileCandidate candidate = new PaymentReconcileCandidate(payment.getId(),
					payment.getOrder().getId(), tossOrderId);
			lateResultApplier.apply(candidate, lookup, "settlement");
			counters.applied++;
			return;
		}
		if (matches(payment.getStatus(), lookup.status().name())) {
			counters.matched++;
			return;
		}
		log.error("결제 거래 대조 불일치: paymentId={}, orderId={}, db={}, toss={}", payment.getId(), tossOrderId,
				payment.getStatus(), lookup.status());
		alertNotifier.notify(Alert.critical("payment.settlement-mismatch",
				"거래 대조 불일치: paymentId=" + payment.getId() + ", orderId=" + tossOrderId + ", db=" + payment.getStatus()
						+ ", toss=" + lookup.status(),
				"paymentId=" + payment.getId()));
		counters.mismatched++;
	}

	/** DB DONE↔토스 DONE, DB CANCELED↔토스 CANCELED, DB FAILED↔토스 ABORTED/EXPIRED 만 일치로 본다. */
	private boolean matches(PaymentStatus dbStatus, String tossStatus) {
		return switch (dbStatus) {
			case DONE -> "DONE".equals(tossStatus);
			case CANCELED -> CANCELED_TOSS_STATUS.equals(tossStatus);
			case FAILED -> "ABORTED".equals(tossStatus) || "EXPIRED".equals(tossStatus);
			case READY, UNKNOWN, CANCEL_REQUESTED -> false;
		};
	}

	/** transactionAt 이 최대인 거래를 남기고, 같으면 취소 계열(CANCELED/PARTIAL_CANCELED)을 우선한다. */
	private Map<String, PaymentTransaction> pickLastTransactionPerOrder(List<PaymentTransaction> transactions) {
		Map<String, PaymentTransaction> lastByOrderId = new LinkedHashMap<>();
		for (PaymentTransaction transaction : transactions) {
			lastByOrderId.merge(transaction.tossOrderId(), transaction, this::pickLater);
		}
		return lastByOrderId;
	}

	private PaymentTransaction pickLater(PaymentTransaction current, PaymentTransaction candidate) {
		int comparison = compareTransactionAt(candidate.transactionAt(), current.transactionAt());
		if (comparison != 0) {
			return comparison > 0 ? candidate : current;
		}
		if (isCancelLike(candidate.status()) && !isCancelLike(current.status())) {
			return candidate;
		}
		return current;
	}

	private int compareTransactionAt(LocalDateTime left, LocalDateTime right) {
		if (left == null && right == null) {
			return 0;
		}
		if (left == null) {
			return -1;
		}
		if (right == null) {
			return 1;
		}
		return left.compareTo(right);
	}

	private boolean isCancelLike(String tossStatus) {
		return CANCELED_TOSS_STATUS.equals(tossStatus) || PARTIAL_CANCELED_TOSS_STATUS.equals(tossStatus);
	}

	private static final class Counters {

		private int matched;
		private int applied;
		private int mismatched;
		private int unknown;
		private int failed;
	}
}
