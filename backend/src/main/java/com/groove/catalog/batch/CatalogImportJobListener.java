package com.groove.catalog.batch;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.groove.notification.service.NewPressingEvent;
import com.groove.product.entity.Album;
import com.groove.product.repository.AlbumRepository;
import com.groove.recommend.service.ProductCatalogChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 잡 종료 시 처리 건수와 소요 시간을 요약해 남기고, 실제 적재분이 있으면 새 프레싱 알림을 발행한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogImportJobListener implements JobExecutionListener {

	private final AlbumRepository albumRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	public void afterJob(JobExecution jobExecution) {
		long readCount = 0;
		long writeCount = 0;
		long skipCount = 0;
		long filterCount = 0;
		for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
			readCount += stepExecution.getReadCount();
			writeCount += stepExecution.getWriteCount();
			skipCount += stepExecution.getSkipCount();
			filterCount += stepExecution.getFilterCount();
		}

		Long masterId = jobExecution.getJobParameters().getLong(CatalogImportJobConfig.PARAM_MASTER_ID);
		log.info("카탈로그 적재 잡 종료: status={} discogsMasterId={} read={} write={} skip={} filter={} duration={}",
				jobExecution.getStatus(), masterId, readCount, writeCount, skipCount, filterCount,
				duration(jobExecution));

		// FAILED 로 끝나도 청크 커밋으로 실제 적재된 프레싱이 있으면 알려야 하므로 status 로 거르지 않는다.
		if (writeCount > 0) {
			publishNewPressingEvent(masterId);
			eventPublisher.publishEvent(new ProductCatalogChangedEvent());
		}
	}

	// discogsMasterImportJob 은 masterId 하나로 돌고, DiscogsReleaseEnrichProcessor 가 만드는
	// 모든 아이템에 그 masterId 가 실려 CatalogImportRegistrar.resolveAlbum() 이 항상 같은 앨범으로 묶는다.
	// 따라서 잡 하나당 앨범 하나, 알림도 하나면 된다.
	private void publishNewPressingEvent(Long masterId) {
		albumRepository.findByDiscogsMasterId(masterId)
				.ifPresent(album -> eventPublisher.publishEvent(toEvent(album)));
	}

	private NewPressingEvent toEvent(Album album) {
		return new NewPressingEvent(album.getId(), album.getTitle());
	}

	private Duration duration(JobExecution jobExecution) {
		LocalDateTime startTime = jobExecution.getStartTime();
		LocalDateTime endTime = jobExecution.getEndTime();
		if (startTime == null || endTime == null) {
			return Duration.ZERO;
		}
		return Duration.between(startTime, endTime);
	}
}
