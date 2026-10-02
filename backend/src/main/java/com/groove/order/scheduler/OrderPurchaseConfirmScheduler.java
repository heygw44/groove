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
import com.groove.order.config.OrderPurchaseConfirmProperties;
import com.groove.order.dto.OrderItemConfirmCandidate;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.service.OrderPurchaseConfirmLock;
import com.groove.order.service.OrderPurchaseConfirmService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 배송완료(delivered_at) 뒤 설정 일수가 지나도 구매확정하지 않은 상품주문을 자동으로 구매확정한다(D7). 진행 중인
 * 반품 클레임이 있으면 건너뛴다. 한 건 실패가 나머지를 막지 않도록 상품주문마다 서비스 트랜잭션을 따로 탄다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPurchaseConfirmScheduler {

	private static final List<OrderItemClaimStatus> IN_PROGRESS_CLAIM_STATUSES = List.of(
			OrderItemClaimStatus.CANCEL_REQUEST, OrderItemClaimStatus.RETURN_REQUEST,
			OrderItemClaimStatus.COLLECTING);

	private static final int MAX_ROUNDS = 50;

	private static final int ALERT_MAX_LISTED_IDS = 10;

	// 첫 바퀴는 nullable 파라미터 대신 모든 행보다 앞선 센티널 커서로 조회한다.
	private static final LocalDateTime INITIAL_CURSOR_DELIVERED_AT = LocalDateTime.of(1970, 1, 1, 0, 0);
	private static final long INITIAL_CURSOR_ID = 0L;

	private final OrderItemRepository orderItemRepository;
	private final OrderPurchaseConfirmService orderPurchaseConfirmService;
	private final OrderPurchaseConfirmLock orderPurchaseConfirmLock;
	private final OrderPurchaseConfirmProperties properties;
	private final ShutdownSignal shutdownSignal;
	private final Clock clock;
	private final AlertNotifier alertNotifier;

	@Scheduled(cron = "${groove.order.purchase-confirm.cron}", zone = "Asia/Seoul")
	public void confirmPurchases() {
		if (shutdownSignal.isShuttingDown()) {
			return;
		}
		boolean acquired = orderPurchaseConfirmLock.runExclusively(this::runConfirmPurchases);
		if (!acquired) {
			log.debug("자동 구매확정 락 획득 실패로 건너뛴다");
		}
	}

	private void runConfirmPurchases() {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime cutoff = now.minusDays(properties.days());
		LocalDateTime afterDeliveredAt = INITIAL_CURSOR_DELIVERED_AT;
		long afterId = INITIAL_CURSOR_ID;
		int candidateTotal = 0;
		int processedTotal = 0;
		List<Long> failedIds = new ArrayList<>();
		for (int round = 0; round < MAX_ROUNDS; round++) {
			if (shutdownSignal.isShuttingDown()) {
				log.info("셧다운 신호로 자동 구매확정 중단 processed={}", processedTotal);
				break;
			}
			List<OrderItemConfirmCandidate> candidates = orderItemRepository.findConfirmCandidates(
					OrderItemStatus.DELIVERED, cutoff, IN_PROGRESS_CLAIM_STATUSES, afterDeliveredAt, afterId,
					Limit.of(properties.batchSize()));
			if (candidates.isEmpty()) {
				break;
			}
			candidateTotal += candidates.size();
			processedTotal += processRound(candidates, cutoff, now, failedIds);
			// 실패·건너뛴 행은 DELIVERED 로 남아 커서 없이 다시 조회하면 매번 앞자리를 차지해 뒤 정상 건을 굶긴다.
			// 성공 행은 DELIVERED 를 벗어나므로 이번 바퀴 마지막 후보 뒤로 커서를 옮겨도 놓치는 행이 없다.
			OrderItemConfirmCandidate last = candidates.get(candidates.size() - 1);
			afterDeliveredAt = last.deliveredAt();
			afterId = last.id();
			if (candidates.size() < properties.batchSize()) {
				break;
			}
		}
		if (processedTotal > 0 || !failedIds.isEmpty()) {
			log.info("자동 구매확정 처리 완료 candidates={} processed={} failed={}", candidateTotal, processedTotal,
					failedIds.size());
		}
		if (!failedIds.isEmpty()) {
			alertFailures(failedIds);
		}
	}

	private int processRound(List<OrderItemConfirmCandidate> candidates, LocalDateTime cutoff, LocalDateTime now,
			List<Long> failedIds) {
		int processed = 0;
		for (OrderItemConfirmCandidate candidate : candidates) {
			if (shutdownSignal.isShuttingDown()) {
				break;
			}
			try {
				if (orderPurchaseConfirmService.confirm(candidate.id(), cutoff, now)) {
					processed++;
				}
			} catch (RuntimeException e) {
				log.error("자동 구매확정 처리 실패 orderItemId={}", candidate.id(), e);
				failedIds.add(candidate.id());
			}
		}
		return processed;
	}

	// 실패 행은 사람이 고칠 때까지 매일 다시 실패하므로 CRITICAL 로 한 번에 묶어 알린다.
	private void alertFailures(List<Long> failedIds) {
		List<Long> shown = failedIds.subList(0, Math.min(failedIds.size(), ALERT_MAX_LISTED_IDS));
		String summary = "자동 구매확정 실패 " + failedIds.size() + "건: orderItemIds=" + shown;
		if (failedIds.size() > shown.size()) {
			summary += " 외 " + (failedIds.size() - shown.size()) + "건";
		}
		alertNotifier.notify(Alert.critical("order.purchase-confirm-failed", summary,
				"orderItemId=" + failedIds.get(0)));
	}
}
