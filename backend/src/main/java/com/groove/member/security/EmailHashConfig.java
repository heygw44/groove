package com.groove.member.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 이메일 점유 해시 시크릿 설정 등록 (#186).
 *
 * <p>이 프로젝트는 {@code @ConfigurationPropertiesScan} 대신 도메인별 명시 등록을 쓴다
 * ({@code MockPaymentConfig}, {@code SecurityConfig} 등). member 도메인이 소유하는
 * {@link EmailHashProperties} 를 auth 의 {@code SecurityConfig} 에 섞지 않고 별도로 등록한다.
 */
@Configuration
@EnableConfigurationProperties(EmailHashProperties.class)
public class EmailHashConfig {
}
