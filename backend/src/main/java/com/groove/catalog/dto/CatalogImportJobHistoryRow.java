package com.groove.catalog.dto;

import java.time.LocalDateTime;

import org.springframework.batch.core.BatchStatus;

/**
 * {@code CatalogImportJobQueryMapper.findLatestExecutions} 프로젝션 전용.
 * 잡 실행 하나에 스텝이 여러 건이면 스텝 실행 id 오름차순으로 행이 반복되고, 스텝이 없으면 스텝 관련 컬럼은 전부 null 이다.
 */
public record CatalogImportJobHistoryRow(
		Long jobInstanceId,
		Long jobExecutionId,
		BatchStatus status,
		LocalDateTime startTime,
		LocalDateTime endTime,
		String jobExitMessage,
		Long discogsMasterId,
		Long stepExecutionId,
		Long readCount,
		Long writeCount,
		Long filterCount,
		Long readSkipCount,
		Long writeSkipCount,
		Long processSkipCount,
		String stepExitMessage
) {
}
