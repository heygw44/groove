package com.groove.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 배송완료 뒤 자동 구매확정 스케줄러 설정(D7). */
@ConfigurationProperties(prefix = "groove.order.purchase-confirm")
public record OrderPurchaseConfirmProperties(
	@DefaultValue("8") int days,
	@DefaultValue("0 30 3 * * *") String cron,
	@DefaultValue("200") int batchSize
) {
}
