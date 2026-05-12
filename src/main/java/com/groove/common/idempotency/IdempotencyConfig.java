package com.groove.common.idempotency;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 멱등성 인프라 구성.
 *
 * <p>{@link IdempotencyProperties} 바인딩을 활성화하고, {@link IdempotencyService} / 정리 태스크가
 * 쓰는 {@code REQUIRES_NEW} 트랜잭션 템플릿 빈을 제공한다 — 짧은 독립 트랜잭션(마커 INSERT, 완료
 * UPDATE, 마커 회수, 정리 배치)을 호출자의 트랜잭션 컨텍스트와 무관하게 커밋하기 위함이다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfig {

    static final String REQUIRES_NEW_TX_TEMPLATE = "idempotencyRequiresNewTransactionTemplate";

    @Bean(name = REQUIRES_NEW_TX_TEMPLATE)
    public TransactionTemplate idempotencyRequiresNewTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
