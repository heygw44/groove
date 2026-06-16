package com.groove.common.transaction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 도메인 공용 트랜잭션 인프라. REQUIRES_NEW 전파의 TransactionTemplate 빈을 제공한다. */
@Configuration(proxyBeanMethods = false)
public class CommonTransactionConfig {

    public static final String REQUIRES_NEW_TX_TEMPLATE = "requiresNewTransactionTemplate";

    @Bean(name = REQUIRES_NEW_TX_TEMPLATE)
    public TransactionTemplate requiresNewTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
