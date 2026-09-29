package com.groove.order.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.order.config.OrderAutoDeliverProperties;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.service.OrderAutoDeliverLock;
import com.groove.order.service.OrderAutoDeliverService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 택배 API 가 없어 발송(shipped_at) 뒤 설정 일수가 지난 상품주문을 배송완료로 자동 처리한다(D7). 한 건 실패가
 * 나머지를 막지 않도록 상품주문마다 서비스 트랜잭션을 따로 탄다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderAutoDeliverScheduler {

	private final OrderItemRepository orderItemRepository;
	private final OrderAutoDeliverService orderAutoDeliverService;
	private final OrderAutoDeliverLock orderAutoDeliverLock;
	private final OrderAutoDeliverProperties properties;
	private final ShutdownSignal shutdownSignal;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${groove.order.auto-deliver.interval}", initialDelay = 20_000)
	public void deliverOrders() {
		if (shutdownSignal.isShuttingDown()) {
			return;
		}
		boolean acquired = orderAutoDeliverLock.runExclusively(this::runDeliverOrders);
		if (!acquired) {
			log.debug("자동 배송완료 락 획득 실패로 건너뛴다");
		}
	}

	private void runDeliverOrders() {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime cutoff = now.minusDays(properties.days());
		List<Long> itemIds = orderItemRepository.findIdsByStatusAndShippedAtBefore(OrderItemStatus.SHIPPING, cutoff,
				Limit.of(properties.batchSize()));
		if (itemIds.isEmpty()) {
			return;
		}
		int processed = 0;
		for (Long itemId : itemIds) {
			if (shutdownSignal.isShuttingDown()) {
				log.info("셧다운 신호로 자동 배송완료 중단 processed={} remaining={}", processed, itemIds.size() - processed);
				break;
			}
			try {
				if (orderAutoDeliverService.deliver(itemId, cutoff, now)) {
					processed++;
				}
			} catch (RuntimeException e) {
				log.warn("자동 배송완료 처리 실패 orderItemId={}", itemId, e);
			}
		}
		if (processed > 0) {
			log.info("자동 배송완료 처리 완료 candidates={} processed={}", itemIds.size(), processed);
		}
	}
}
