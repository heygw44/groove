package com.groove.order.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.order.config.OrderPurchaseConfirmProperties;
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

	private final OrderItemRepository orderItemRepository;
	private final OrderPurchaseConfirmService orderPurchaseConfirmService;
	private final OrderPurchaseConfirmLock orderPurchaseConfirmLock;
	private final OrderPurchaseConfirmProperties properties;
	private final ShutdownSignal shutdownSignal;
	private final Clock clock;

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
		List<Long> itemIds = orderItemRepository.findIdsByStatusAndDeliveredAtBeforeAndClaimNotInProgress(
				OrderItemStatus.DELIVERED, cutoff, IN_PROGRESS_CLAIM_STATUSES, Limit.of(properties.batchSize()));
		if (itemIds.isEmpty()) {
			return;
		}
		int processed = 0;
		for (Long itemId : itemIds) {
			if (shutdownSignal.isShuttingDown()) {
				log.info("셧다운 신호로 자동 구매확정 중단 processed={} remaining={}", processed, itemIds.size() - processed);
				break;
			}
			try {
				if (orderPurchaseConfirmService.confirm(itemId, cutoff, now)) {
					processed++;
				}
			} catch (RuntimeException e) {
				log.warn("자동 구매확정 처리 실패 orderItemId={}", itemId, e);
			}
		}
		if (processed > 0) {
			log.info("자동 구매확정 처리 완료 candidates={} processed={}", itemIds.size(), processed);
		}
	}
}
