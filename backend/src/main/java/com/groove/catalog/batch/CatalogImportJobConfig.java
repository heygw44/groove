package com.groove.catalog.batch;

import java.math.BigDecimal;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse.Version;
import com.groove.catalog.config.CatalogImportProperties;
import com.groove.catalog.dto.CatalogImportItem;
import com.groove.catalog.service.DiscogsReleaseMapper;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/** Discogs 마스터 릴리즈 하나를 상품으로 일괄 등록하는 배치 잡. */
@Configuration
@RequiredArgsConstructor
public class CatalogImportJobConfig {

	public static final String JOB_NAME = "discogsMasterImportJob";
	public static final String STEP_NAME = "discogsMasterImportStep";
	public static final String PARAM_MASTER_ID = "discogsMasterId";
	public static final String PARAM_DEFAULT_PRICE = "defaultPrice";
	public static final String PARAM_REQUESTED_AT = "requestedAt";

	@Bean
	@StepScope
	public DiscogsMasterVersionsReader discogsMasterVersionsReader(PressingLookupClient client,
			@Value("#{jobParameters['discogsMasterId']}") Long masterId) {
		return new DiscogsMasterVersionsReader(client, masterId);
	}

	@Bean
	@StepScope
	public DiscogsReleaseEnrichProcessor discogsReleaseEnrichProcessor(PressingLookupClient client,
			ProductRepository productRepository, GenreRepository genreRepository, DiscogsReleaseMapper mapper,
			@Value("#{jobParameters['discogsMasterId']}") Long masterId,
			@Value("#{jobParameters['defaultPrice']}") String defaultPrice) {
		return new DiscogsReleaseEnrichProcessor(client, productRepository, genreRepository, mapper, masterId,
				new BigDecimal(defaultPrice));
	}

	@Bean
	public Step discogsMasterImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			DiscogsMasterVersionsReader reader, DiscogsReleaseEnrichProcessor processor, PressingUpsertWriter writer,
			CatalogImportStepListener stepListener, CatalogImportProperties properties) {
		ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
		backOff.setInitialInterval(properties.backoffInitial().toMillis());
		backOff.setMultiplier(2.0);
		backOff.setMaxInterval(properties.backoffMax().toMillis());

		return new StepBuilder(STEP_NAME, jobRepository)
				.<Version, CatalogImportItem>chunk(properties.chunkSize(), transactionManager)
				.reader(reader)
				.processor(processor)
				.writer(writer)
				.faultTolerant()
				// Writer 가 롤백돼도 Discogs 재호출이 일어나지 않도록 프로세서는 트랜잭션 밖에서 실행한다.
				.processorNonTransactional()
				.skip(CatalogItemException.class)
				.skipLimit(properties.skipLimit())
				// 조회(reader) 실패는 페이지 단위라 재시도 대상에서 제외하고 즉시 Step 을 실패시킨다.
				.retry(CatalogTransientException.class)
				.retryLimit(properties.retryLimit())
				.backOffPolicy(backOff)
				.listener((StepExecutionListener) stepListener)
				.listener((SkipListener<Version, CatalogImportItem>) stepListener)
				.build();
	}

	@Bean
	public Job discogsMasterImportJob(JobRepository jobRepository, Step discogsMasterImportStep,
			CatalogImportJobListener listener) {
		return new JobBuilder(JOB_NAME, jobRepository)
				.listener(listener)
				.start(discogsMasterImportStep)
				.build();
	}
}
