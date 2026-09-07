package com.groove.notification.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 알림 적재는 부가 기능이라, 실패해도 재고 조정·상품 수정·카탈로그 적재 등 원 트랜잭션을 롤백시키면 안 된다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private final NotificationDispatcher notificationDispatcher;

	// fallbackExecution = true: 배치 잡의 afterJob 콜백은 트랜잭션 밖에서 이벤트를 발행하므로 AFTER_COMMIT 만으로는
	// 수신되지 않는다.
	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void handleRestock(RestockEvent event) {
		try {
			notificationDispatcher.dispatchRestock(event);
		} catch (RuntimeException e) {
			log.warn("재입고 알림 적재 실패 productId={}", event.productId(), e);
		}
	}

	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void handlePriceDrop(PriceDropEvent event) {
		try {
			notificationDispatcher.dispatchPriceDrop(event);
		} catch (RuntimeException e) {
			log.warn("가격 인하 알림 적재 실패 productId={}", event.productId(), e);
		}
	}

	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void handleNewPressing(NewPressingEvent event) {
		try {
			notificationDispatcher.dispatchNewPressing(event);
		} catch (RuntimeException e) {
			log.warn("새 프레싱 알림 적재 실패 albumId={}", event.albumId(), e);
		}
	}
}
