package com.groove.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 결제 대사 스케줄러 설정. grace 는 진행 중인 승인 호출(prepare 락 대기 + 토스 confirm + 재조회 흡수 + approve
 * 락 대기, 최악 90초 안쪽)을 대사가 건드리지 않기 위한 여유다.
 *
 * <p>refundRetryGrace 는 결과불명으로 REQUESTED 에 남은 부분취소(payment_cancel)를 같은 idempotencyKey 로
 * 재호출하기까지 기다리는 시간이다. payment_cancel 에는 재시도 횟수 컬럼을 두지 않고, requestedAt 로부터
 * refundRetryGrace 를 maxAttempts 번 기다린 뒤({@link #refundVerifyAfter()})부터는 재호출 대신 토스 결제
 * 조회로 확인만 한다.</p>
 */
@ConfigurationProperties(prefix = "groove.payment.reconcile")
public record PaymentReconcileProperties(
	@DefaultValue("60s") Duration interval,
	@DefaultValue("2m") Duration grace,
	@DefaultValue("50") int batchSize,
	@DefaultValue("10") int maxAttempts,
	@DefaultValue("1m") Duration refundRetryGrace
) {

	/** refundRetryGrace 를 maxAttempts 번 기다린 시점. 이후로는 재호출 대신 토스 조회로만 확인한다. */
	public Duration refundVerifyAfter() {
		return refundRetryGrace.multipliedBy(maxAttempts);
	}
}
