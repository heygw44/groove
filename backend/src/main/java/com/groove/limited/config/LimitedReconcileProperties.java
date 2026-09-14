package com.groove.limited.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 한정반 Redis 대사 스케줄러 설정. grace 는 reserve→write 커밋 최악 소요(드롭 행 락 대기 포함)보다 넉넉히 둔 여유다. */
@ConfigurationProperties(prefix = "groove.limited.reconcile")
public record LimitedReconcileProperties(
	@DefaultValue("30s") Duration interval,
	@DefaultValue("30s") Duration grace
) {
}
