package com.groove.global.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.stats.repository.SalesDailyRepository;
import com.groove.stats.service.SalesAggregationService;
import com.groove.support.IntegrationTestSupport;

/**
 * {@link LocalDataInitializer} 은 local/seed 프로파일에서만 빈으로 뜨는 {@code ApplicationRunner} 라
 * test 프로파일 컨텍스트에 그대로 올릴 수 없다. 대신 {@code runStatsBackfill()} 이 실제로 의존하는 메커니즘 —
 * {@code afterCommit()} 콜백 안에서 {@code PROPAGATION_REQUIRES_NEW} 로 집계를 호출하면 남은 트랜잭션
 * 동기화와 무관하게 새 트랜잭션이 실제로 열리는지 — 를 같은 방식으로 재현해 검증한다.
 */
class LocalDataInitializerTransactionIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate SALE_DATE = LocalDate.of(2032, 1, 10);

	@Autowired
	private SalesAggregationService salesAggregationService;

	@Autowired
	private SalesDailyRepository salesDailyRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Nested
	@DisplayName("afterCommit() 콜백에서 REQUIRES_NEW 로 집계를 호출하면")
	class AggregateInAfterCommitCallback {

		@Test
		@DisplayName("남은 트랜잭션 동기화와 무관하게 새 트랜잭션을 열어 집계가 커밋된다")
		void aggregatesSuccessfullyAfterOuterTransactionCommits() {
			// given
			TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

			// when
			assertThatCode(() -> outerTransaction.executeWithoutResult(status ->
					TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
						@Override
						public void afterCommit() {
							DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
							definition.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW);
							TransactionTemplate requiresNew = new TransactionTemplate(transactionManager, definition);
							requiresNew.executeWithoutResult(
									inner -> salesAggregationService.aggregateDate(SALE_DATE));
						}
					})))
					.doesNotThrowAnyException();

			// then
			assertThat(salesDailyRepository.findById(SALE_DATE)).isPresent();
		}
	}
}
