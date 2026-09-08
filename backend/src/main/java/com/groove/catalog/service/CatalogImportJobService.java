package com.groove.catalog.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Service;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.catalog.batch.CatalogImportJobConfig;
import com.groove.catalog.dto.CatalogImportJobHistoryRow;
import com.groove.catalog.dto.CatalogImportJobRequest;
import com.groove.catalog.dto.CatalogImportJobResponse;
import com.groove.catalog.dto.CatalogImportJobStartResponse;
import com.groove.catalog.mapper.CatalogImportJobQueryMapper;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;

import lombok.RequiredArgsConstructor;

/** Discogs 마스터 버전 전체 적재 배치 잡을 시작/조회/재시작한다. */
@Service
@RequiredArgsConstructor
public class CatalogImportJobService {

	private final Job discogsMasterImportJob;
	private final JobLauncher jobLauncher;
	private final JobExplorer jobExplorer;
	private final JobOperator jobOperator;
	private final CatalogImportJobQueryMapper catalogImportJobQueryMapper;
	private final AdminAuditLogService adminAuditLogService;
	private final Clock clock;

	public CatalogImportJobStartResponse start(Long adminId, CatalogImportJobRequest request) {
		Long masterId = request.discogsMasterId();
		boolean alreadyRunning = jobExplorer.findRunningJobExecutions(CatalogImportJobConfig.JOB_NAME).stream()
				.anyMatch(execution -> Objects.equals(masterId,
						execution.getJobParameters().getLong(CatalogImportJobConfig.PARAM_MASTER_ID)));
		if (alreadyRunning) {
			throw new BusinessException(ErrorCode.CATALOG_IMPORT_JOB_RUNNING);
		}

		JobParameters jobParameters = new JobParametersBuilder()
				.addLong(CatalogImportJobConfig.PARAM_MASTER_ID, masterId)
				.addString(CatalogImportJobConfig.PARAM_DEFAULT_PRICE, request.defaultPrice().toPlainString(), false)
				.addLocalDateTime(CatalogImportJobConfig.PARAM_REQUESTED_AT, LocalDateTime.now(clock))
				.toJobParameters();

		JobExecution execution;
		try {
			execution = jobLauncher.run(discogsMasterImportJob, jobParameters);
		} catch (JobExecutionAlreadyRunningException | JobRestartException | JobInstanceAlreadyCompleteException
				| JobParametersInvalidException e) {
			throw new BusinessException(ErrorCode.CATALOG_IMPORT_JOB_RUNNING);
		}

		adminAuditLogService.record(adminId, AdminAuditAction.CATALOG_IMPORT_JOB_START,
				AdminAuditTargetType.CATALOG_IMPORT_JOB, execution.getId(), "discogsMasterId=" + masterId);

		return new CatalogImportJobStartResponse(execution.getId());
	}

	public PageResponse<CatalogImportJobResponse> list(int page, int size) {
		long total;
		try {
			total = jobExplorer.getJobInstanceCount(CatalogImportJobConfig.JOB_NAME);
		} catch (NoSuchJobException e) {
			return PageResponse.of(List.of(), page, size, 0);
		}

		List<JobInstance> instances = jobExplorer.getJobInstances(CatalogImportJobConfig.JOB_NAME, page * size, size);
		List<CatalogImportJobResponse> content = latestExecutionResponses(instances);

		return PageResponse.of(content, page, size, total);
	}

	public CatalogImportJobResponse get(Long jobExecutionId) {
		JobExecution execution = findExecution(jobExecutionId);
		return CatalogImportJobResponse.from(execution);
	}

	public CatalogImportJobStartResponse restart(Long jobExecutionId) {
		JobExecution execution = findExecution(jobExecutionId);
		if (execution.getStatus() != BatchStatus.FAILED) {
			throw new BusinessException(ErrorCode.COMMON_CONFLICT);
		}

		long newExecutionId;
		try {
			newExecutionId = jobOperator.restart(jobExecutionId);
		} catch (JobInstanceAlreadyCompleteException | NoSuchJobExecutionException | NoSuchJobException
				| JobRestartException | JobParametersInvalidException e) {
			throw new BusinessException(ErrorCode.COMMON_CONFLICT);
		}

		return new CatalogImportJobStartResponse(newExecutionId);
	}

	/**
	 * 인스턴스마다 {@code jobExplorer.getJobExecutions()} 를 반복 호출하던 N+1 을 매퍼 한 번 호출로 대체한다.
	 * 매퍼 결과를 인스턴스 id 로 그룹핑한 뒤, {@code instances} 가 준 정렬 순서(인스턴스 id 내림차순)를 그대로 지킨다.
	 */
	private List<CatalogImportJobResponse> latestExecutionResponses(List<JobInstance> instances) {
		if (instances.isEmpty()) {
			return List.of();
		}

		List<Long> instanceIds = instances.stream().map(JobInstance::getInstanceId).toList();
		List<CatalogImportJobHistoryRow> rows = catalogImportJobQueryMapper.findLatestExecutions(instanceIds,
				CatalogImportJobConfig.PARAM_MASTER_ID);
		Map<Long, List<CatalogImportJobHistoryRow>> rowsByInstanceId = rows.stream()
				.collect(Collectors.groupingBy(CatalogImportJobHistoryRow::jobInstanceId, LinkedHashMap::new,
						Collectors.toList()));

		return instanceIds.stream()
				.map(rowsByInstanceId::get)
				.filter(Objects::nonNull)
				.map(CatalogImportJobResponse::fromRows)
				.toList();
	}

	private JobExecution findExecution(Long jobExecutionId) {
		JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
		if (execution == null || !CatalogImportJobConfig.JOB_NAME.equals(execution.getJobInstance().getJobName())) {
			throw new BusinessException(ErrorCode.CATALOG_IMPORT_JOB_NOT_FOUND);
		}
		return execution;
	}
}
