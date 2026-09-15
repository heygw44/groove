package com.groove.global.alert;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 정합성 경보(Discord 웹훅) 설정. discordWebhookUrl 이 비어 있으면 LoggingAlertNotifier 가 대신 주입된다. */
@ConfigurationProperties(prefix = "groove.alert")
public record AlertProperties(
		@DefaultValue("") String discordWebhookUrl,
		@DefaultValue("5m") Duration suppressWindow,
		@DefaultValue("100") int queueCapacity,
		@DefaultValue("2s") Duration timeout) {
}
