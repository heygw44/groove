package com.groove.catalog.dto;

import java.time.LocalDateTime;
import java.util.List;

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

	/**
	 * {@code CatalogImportJobQueryMapper.findLatestExecutions} 가 준, 같은 잡 실행에 속한 스텝별 행을 합산한다.
	 * {@link #from(JobExecution)} 과 정확히 같은 규칙(스텝 합산·스텝 실행 id 오름차순 첫 exitMessage·1000자 절단)을 따른다.
	 * rows 는 같은 jobExecutionId 를 공유하며 stepExecutionId 오름차순으로 정렬돼 있어야 한다.
	 */
	public static CatalogImportJobResponse fromRows(List<CatalogImportJobHistoryRow> rows) {
		CatalogImportJobHistoryRow first = rows.get(0);
		long readCount = 0;
		long writeCount = 0;
		long skipCount = 0;
		long filterCount = 0;
		String exitMessage = null;
		for (CatalogImportJobHistoryRow row : rows) {
			if (row.stepExecutionId() == null) {
				continue;
			}
			readCount += row.readCount();
			writeCount += row.writeCount();
			filterCount += row.filterCount();
			skipCount += row.readSkipCount() + row.writeSkipCount() + row.processSkipCount();
			if (exitMessage == null && row.stepExitMessage() != null && !row.stepExitMessage().isBlank()) {
				exitMessage = row.stepExitMessage();
			}
		}
		if (exitMessage == null) {
			String description = first.jobExitMessage();
			exitMessage = description == null || description.isBlank() ? null : description;
		}
		if (exitMessage != null && exitMessage.length() > EXIT_MESSAGE_MAX_LENGTH) {
			exitMessage = exitMessage.substring(0, EXIT_MESSAGE_MAX_LENGTH);
		}

		return new CatalogImportJobResponse(first.jobExecutionId(), first.discogsMasterId(), first.status(),
				readCount, writeCount, skipCount, filterCount, first.startTime(), first.endTime(), exitMessage);
	}
}
