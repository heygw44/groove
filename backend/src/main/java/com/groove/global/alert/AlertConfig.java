package com.groove.global.alert;

import java.time.Clock;
import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableConfigurationProperties(AlertProperties.class)
public class AlertConfig {

	@Bean
	public ThreadPoolTaskExecutor alertExecutor(AlertProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(properties.queueCapacity());
		executor.setThreadNamePrefix("groove-alert-");
		executor.setRejectedExecutionHandler((runnable, taskExecutor) -> log.warn(
				"alertExecutor 큐 포화로 경보를 버림 activeCount={} queueSize={}", taskExecutor.getActiveCount(),
				taskExecutor.getQueue().size()));
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(2);
		executor.initialize();
		return executor;
	}

	@Bean
	public RestClient alertRestClient(RestClient.Builder builder, AlertProperties properties,
			ClientHttpRequestFactoryBuilder<?> factoryBuilder, ClientHttpRequestFactorySettings factorySettings) {
		ClientHttpRequestFactory requestFactory = factoryBuilder.build(
				factorySettings.withTimeouts(properties.timeout(), properties.timeout()));
		return builder.requestFactory(requestFactory).build();
	}

	@Bean
	public AlertNotifier alertNotifier(AlertProperties properties, RestClient alertRestClient, Executor alertExecutor,
			Clock clock, Environment environment) {
		if (properties.discordWebhookUrl() == null || properties.discordWebhookUrl().isBlank()) {
			return new LoggingAlertNotifier();
		}
		AlertThrottle throttle = new AlertThrottle(clock, properties.suppressWindow());
		String serviceLabel = "groove-" + firstActiveProfileOrDefault(environment);
		return new DiscordWebhookAlertNotifier(alertRestClient, properties.discordWebhookUrl(), alertExecutor,
				throttle, clock, serviceLabel);
	}

	private String firstActiveProfileOrDefault(Environment environment) {
		String[] activeProfiles = environment.getActiveProfiles();
		return activeProfiles.length > 0 ? activeProfiles[0] : "default";
	}
}
