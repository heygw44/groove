package com.groove.member.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 이메일 점유 해시 시크릿 설정(EmailHashProperties) 등록. */
@Configuration
@EnableConfigurationProperties(EmailHashProperties.class)
public class EmailHashConfig {
}
