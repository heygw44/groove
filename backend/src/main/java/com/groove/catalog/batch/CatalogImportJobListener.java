package com.groove.catalog.batch;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 잡 종료 시 처리 건수와 소요 시간을 요약해 남긴다. */
@Slf4j
@Component
public class CatalogImportJobListener implements JobExecutionListener {

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
