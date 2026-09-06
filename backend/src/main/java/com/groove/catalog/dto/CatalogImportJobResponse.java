package com.groove.catalog.dto;

import java.time.LocalDateTime;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

import com.groove.catalog.batch.CatalogImportJobConfig;

public record CatalogImportJobResponse(
		Long jobExecutionId,
		Long discogsMasterId,
		BatchStatus status,
		long readCount,
		long writeCount,
		long skipCount,
		long filterCount,
		LocalDateTime startedAt,
		LocalDateTime endedAt,
		String exitMessage
) {

	private static final int EXIT_MESSAGE_MAX_LENGTH = 1000;

	public static CatalogImportJobResponse from(JobExecution execution) {
		long readCount = 0;
		long writeCount = 0;
		long skipCount = 0;
		long filterCount = 0;
		String exitMessage = null;
		for (StepExecution stepExecution : execution.getStepExecutions()) {
			readCount += stepExecution.getReadCount();
			writeCount += stepExecution.getWriteCount();
			skipCount += stepExecution.getSkipCount();
			filterCount += stepExecution.getFilterCount();
			if (exitMessage == null) {
				String description = stepExecution.getExitStatus().getExitDescription();
				if (description != null && !description.isBlank()) {
					exitMessage = description;
				}
			}
		}
		if (exitMessage == null) {
			String description = execution.getExitStatus().getExitDescription();
			exitMessage = description == null || description.isBlank() ? null : description;
		}
		if (exitMessage != null && exitMessage.length() > EXIT_MESSAGE_MAX_LENGTH) {
			exitMessage = exitMessage.substring(0, EXIT_MESSAGE_MAX_LENGTH);
		}

		return new CatalogImportJobResponse(execution.getId(),
				execution.getJobParameters().getLong(CatalogImportJobConfig.PARAM_MASTER_ID), execution.getStatus(),
				readCount, writeCount, skipCount, filterCount, execution.getStartTime(), execution.getEndTime(),
				exitMessage);
	}
}
