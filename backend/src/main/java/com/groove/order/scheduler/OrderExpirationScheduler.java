package com.groove.order.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedRelease;
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

	static final int BATCH_SIZE = 100;

	private final OrderRepository orderRepository;
	private final OrderExpirationService orderExpirationService;
	private final LimitedDropRedisService limitedDropRedisService;
	private final OrderExpirationLock orderExpirationLock;
	private final ShutdownSignal shutdownSignal;
	private final Clock clock;

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
		List<Long> orderIds = orderRepository.findIdsByStatusAndExpiresAtBefore(OrderStatus.PENDING, now,
				PaymentStatus.UNRESOLVED, Limit.of(BATCH_SIZE));
		if (orderIds.isEmpty()) {
			return;
		}
		int processed = 0;
		int failed = 0;
		for (Long orderId : orderIds) {
			if (shutdownSignal.isShuttingDown()) {
				log.info("셧다운 신호로 주문 만료 중단 processed={} remaining={}", processed, orderIds.size() - processed);
				break;
			}
			try {
				Optional<LimitedRelease> release = orderExpirationService.expire(orderId, now);
				// Redis 는 DB 커밋 뒤에 풀어야 롤백된 주문의 선점이 새어 나가지 않는다.
				release.ifPresent(r -> limitedDropRedisService.release(r.dropId(), r.memberId()));
			} catch (RuntimeException e) {
				failed++;
				log.warn("주문 만료 처리 실패 orderId={}", orderId, e);
			}
			processed++;
		}
		if (processed > 0) {
			log.info("만료 주문 취소 완료 candidates={} processed={} failed={}", orderIds.size(), processed, failed);
		}
	}
}
