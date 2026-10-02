package com.groove.order.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.order.dto.OrderExpirationCandidate;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderExpirationLock;
import com.groove.order.service.OrderExpirationService;
import com.groove.payment.entity.PaymentStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 결제 기한이 지난 PENDING 주문을 주기적으로 취소한다. 한 건 실패가 나머지를 막지 않도록 주문마다 서비스 트랜잭션을 따로 탄다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationScheduler {

	public static final int BATCH_SIZE = 100;

	private static final int MAX_ROUNDS = 50;

	private static final int ALERT_MAX_LISTED_IDS = 10;

	// 첫 바퀴는 nullable 파라미터 대신 모든 행보다 앞선 센티널 커서로 조회한다.
	private static final LocalDateTime INITIAL_CURSOR_EXPIRES_AT = LocalDateTime.of(1970, 1, 1, 0, 0);
	private static final long INITIAL_CURSOR_ID = 0L;

	private final OrderRepository orderRepository;
	private final OrderExpirationService orderExpirationService;
	private final OrderExpirationLock orderExpirationLock;
	private final ShutdownSignal shutdownSignal;
	private final Clock clock;
	private final AlertNotifier alertNotifier;

	@Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
	public void expireOrders() {
		if (shutdownSignal.isShuttingDown()) {
			return;
		}
		boolean acquired = orderExpirationLock.runExclusively(this::runExpireOrders);
		if (!acquired) {
			log.debug("주문 만료 락 획득 실패로 건너뛴다");
		}
	}

	private void runExpireOrders() {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime afterExpiresAt = INITIAL_CURSOR_EXPIRES_AT;
		long afterId = INITIAL_CURSOR_ID;
		int candidateTotal = 0;
		int processedTotal = 0;
		List<Long> failedIds = new ArrayList<>();
		for (int round = 0; round < MAX_ROUNDS; round++) {
			if (shutdownSignal.isShuttingDown()) {
				log.info("셧다운 신호로 주문 만료 중단 processed={}", processedTotal);
				break;
			}
			List<OrderExpirationCandidate> candidates = orderRepository.findExpirationCandidates(OrderStatus.PENDING,
					now, PaymentStatus.UNRESOLVED, afterExpiresAt, afterId, Limit.of(BATCH_SIZE));
			if (candidates.isEmpty()) {
				break;
			}
			candidateTotal += candidates.size();
			processedTotal += processRound(candidates, now, failedIds);
			// 실패·보류된 행은 PENDING 에 만료 시각 그대로 남아 커서 없이 다시 조회하면 매번 앞자리를 차지해 뒤 주문을 굶긴다.
			// 만료된 행은 PENDING 을 벗어나므로 이번 바퀴 마지막 후보 뒤로 커서를 옮겨도 놓치는 행이 없다.
			OrderExpirationCandidate last = candidates.get(candidates.size() - 1);
			afterExpiresAt = last.expiresAt();
			afterId = last.id();
			if (candidates.size() < BATCH_SIZE) {
				break;
			}
		}
		if (processedTotal > 0 || !failedIds.isEmpty()) {
			log.info("만료 주문 취소 완료 candidates={} processed={} failed={}", candidateTotal, processedTotal,
					failedIds.size());
		}
		if (!failedIds.isEmpty()) {
			alertFailures(failedIds);
		}
	}

	private int processRound(List<OrderExpirationCandidate> candidates, LocalDateTime now, List<Long> failedIds) {
		int processed = 0;
		for (OrderExpirationCandidate candidate : candidates) {
			if (shutdownSignal.isShuttingDown()) {
				break;
			}
			try {
				orderExpirationService.expire(candidate.id(), now);
			} catch (RuntimeException e) {
				log.error("주문 만료 처리 실패 orderId={}", candidate.id(), e);
				failedIds.add(candidate.id());
			}
			processed++;
		}
		return processed;
	}

	// 실패 행은 사람이 고칠 때까지 매 주기 다시 실패하고 재고·쿠폰·한정반 슬롯을 묶어 두므로 CRITICAL 로 한 번에 묶어 알린다.
	private void alertFailures(List<Long> failedIds) {
		List<Long> shown = failedIds.subList(0, Math.min(failedIds.size(), ALERT_MAX_LISTED_IDS));
		String summary = "주문 만료 실패 " + failedIds.size() + "건: orderIds=" + shown;
		if (failedIds.size() > shown.size()) {
			summary += " 외 " + (failedIds.size() - shown.size()) + "건";
		}
		alertNotifier.notify(Alert.critical("order.expiration-failed", summary, "orderId=" + failedIds.get(0)));
	}
}
