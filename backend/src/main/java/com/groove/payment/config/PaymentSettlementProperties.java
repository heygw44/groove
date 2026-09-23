package com.groove.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 하루치 결제를 토스 거래 조회(GET /v1/transactions)와 대조하는 정산 스케줄러 설정. overlap 은 자정 경계에서
 * 생기는 시각 오차(서버-토스 시계, 트랜잭션 커밋 지연)를 흡수하기 위한 여유다.
 */
@ConfigurationProperties(prefix = "groove.payment.settlement")
public record PaymentSettlementProperties(
	@DefaultValue("0 10 5 * * *") String cron,
	@DefaultValue("10m") Duration overlap,
	@DefaultValue("5000") int pageSize,
	@DefaultValue("20") int maxPages
) {
}
