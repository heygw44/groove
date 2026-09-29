package com.groove.order.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 발송 뒤 택배 API 없이 배송완료를 추정하는 자동 배송완료 스케줄러 설정(D7). */
@ConfigurationProperties(prefix = "groove.order.auto-deliver")
public record OrderAutoDeliverProperties(
	@DefaultValue("5") int days,
	@DefaultValue("5m") Duration interval,
	@DefaultValue("200") int batchSize
) {
}
