package com.groove.common.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Spring Data JPA Auditing 활성화 — BaseTimeEntity 의 @CreatedDate/@LastModifiedDate 가 동작하게 한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
