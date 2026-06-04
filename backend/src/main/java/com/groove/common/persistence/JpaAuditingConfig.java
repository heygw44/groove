package com.groove.common.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Spring Data JPA Auditing 활성화.
 *
 * <p>{@link BaseTimeEntity} 의 {@code @CreatedDate}/{@code @LastModifiedDate} 가
 * 동작하도록 한다. 별도 Configuration 으로 분리한 이유: {@code @SpringBootApplication}
 * 메타에 직접 부착하면 슬라이스 테스트에서 우회가 어렵기 때문.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
