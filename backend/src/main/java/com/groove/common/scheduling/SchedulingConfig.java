package com.groove.common.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 앱 전역 @Scheduled 활성화. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
