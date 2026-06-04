package com.groove.common.transaction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 도메인 공용 트랜잭션 인프라.
 *
 * <p>{@code REQUIRES_NEW} TransactionTemplate 빈은 짧은 독립 트랜잭션(멱등 마커 INSERT/완료
 * UPDATE/회수, 멱등·만료 배치 등)을 호출자 트랜잭션 컨텍스트와 무관하게 커밋하기 위한 공통
 * 인프라이므로 도메인별로 복제하지 않고 한 곳에서 제공한다 (#92 리뷰).
 *
 * <p>소비자: {@code IdempotencyService}, {@code IdempotencyRecordCleanupTask},
 * {@code MemberCouponExpirationTask}. 신규 정리/만료 배치는 같은 빈을 {@code @Qualifier(
 * CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE)} 로 주입받아 사용한다.
 */
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
