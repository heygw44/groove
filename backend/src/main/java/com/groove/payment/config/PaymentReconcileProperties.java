package com.groove.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 결제 대사 스케줄러 설정. grace 는 진행 중인 승인 호출(prepare 락 대기 + 토스 confirm + 재조회 흡수 + approve
 * 락 대기, 최악 90초 안쪽)을 대사가 건드리지 않기 위한 여유다.
 */
@ConfigurationProperties(prefix = "groove.payment.reconcile")
public record PaymentReconcileProperties(
	@DefaultValue("60s") Duration interval,
	@DefaultValue("2m") Duration grace,
	@DefaultValue("50") int batchSize,
	@DefaultValue("10") int maxAttempts
) {
}
