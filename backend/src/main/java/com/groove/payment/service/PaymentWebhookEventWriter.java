package com.groove.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.payment.entity.PaymentWebhookEvent;
import com.groove.payment.entity.PaymentWebhookResult;
import com.groove.payment.repository.PaymentWebhookEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 웹훅 이벤트 행의 짧은 트랜잭션 쓰기. 멱등은 사전 조회로 판단한다 — save() 가 JPA 제약 위반으로 실패하면
 * EntityManager 가 rollback-only 로 표시돼, 이 메서드 안에서 예외를 잡아도 트랜잭션 커밋 시점에
 * UnexpectedRollbackException 이 새어 나간다. ERROR 로 끝난 행은 새로 만들지 않고 재사용한다 — 그래야
 * 재조회 실패로 토스가 재전송한 같은 이벤트가 사전 조회 dedup 에 걸려 영영 처리되지 않는 일이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookEventWriter {

	private final PaymentWebhookEventRepository repository;
	private final Clock clock;

	/** 빈 Optional 이면 이미 ERROR 아닌 결과로 처리된 이벤트라 다시 처리하지 않는다(멱등). */
	@Transactional
	public Optional<PaymentWebhookEvent> receive(String eventType, String paymentKey, String tossOrderId,
			String tossStatus, OffsetDateTime eventCreatedAt) {
		LocalDateTime serverEventCreatedAt = toServerTime(eventCreatedAt);
		Optional<PaymentWebhookEvent> existing = repository.findByPaymentKeyAndTossStatusAndEventCreatedAt(paymentKey,
				tossStatus, serverEventCreatedAt);
		if (existing.isPresent()) {
			if (existing.get().getResult() == PaymentWebhookResult.ERROR) {
				return existing;
			}
			log.info("토스 웹훅 중복 이벤트, 다시 처리하지 않음: paymentKey={}, tossStatus={}, eventCreatedAt={}", paymentKey,
					tossStatus, eventCreatedAt);
			return Optional.empty();
		}
		PaymentWebhookEvent event = PaymentWebhookEvent.receive(eventType, paymentKey, tossOrderId, tossStatus,
				serverEventCreatedAt, LocalDateTime.now(clock));
		return Optional.of(repository.save(event));
	}

	@Transactional
	public void markResult(Long eventId, PaymentWebhookResult result, String detail) {
		PaymentWebhookEvent event = repository.findById(eventId)
				.orElseThrow(() -> new IllegalStateException("웹훅 이벤트 행을 찾을 수 없습니다: id=" + eventId));
		event.markProcessed(result, detail, LocalDateTime.now(clock));
	}

	private LocalDateTime toServerTime(OffsetDateTime time) {
		if (time == null) {
			return null;
		}
		return time.atZoneSameInstant(clock.getZone()).toLocalDateTime();
	}
}
