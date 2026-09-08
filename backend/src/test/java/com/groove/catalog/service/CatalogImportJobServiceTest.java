package com.groove.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.batch.core.repository.JobRestartException;

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

@ExtendWith(MockitoExtension.class)
class CatalogImportJobServiceTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-06T01:00:00Z"),
			ZoneId.of("Asia/Seoul"));

	@Mock
	Job discogsMasterImportJob;

	@Mock
	JobLauncher jobLauncher;

	@Mock
	JobExplorer jobExplorer;

	@Mock
	JobOperator jobOperator;

	@Mock
	CatalogImportJobQueryMapper catalogImportJobQueryMapper;

	@Mock
	AdminAuditLogService adminAuditLogService;

	CatalogImportJobService service() {
		return new CatalogImportJobService(discogsMasterImportJob, jobLauncher, jobExplorer, jobOperator,
				catalogImportJobQueryMapper, adminAuditLogService, FIXED_CLOCK);
	}

	private JobInstance jobInstance(long id) {
		return new JobInstance(id, CatalogImportJobConfig.JOB_NAME);
	}

	private JobExecution jobExecution(long id, JobInstance instance, JobParameters parameters, BatchStatus status) {
		JobExecution execution = new JobExecution(instance, id, parameters);
		execution.setStatus(status);
		return execution;
	}

	@Nested
	@DisplayName("start()")
	class Start {

		@Test
		@DisplayName("같은 마스터 릴리즈로 실행 중인 잡이 있으면 CATALOG_IMPORT_JOB_RUNNING 예외를 던진다")
		void throwsWhenAlreadyRunning() {
			// given
			JobParameters runningParams = new JobParametersBuilder()
					.addLong(CatalogImportJobConfig.PARAM_MASTER_ID, 21247L)
					.toJobParameters();
			JobExecution running = jobExecution(1L, jobInstance(1L), runningParams, BatchStatus.STARTED);
			given(jobExplorer.findRunningJobExecutions(CatalogImportJobConfig.JOB_NAME)).willReturn(Set.of(running));
			CatalogImportJobRequest request = new CatalogImportJobRequest(21247L, BigDecimal.valueOf(45000));

			// when & then
			assertThatThrownBy(() -> service().start(1L, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_IMPORT_JOB_RUNNING);
		}

		@Test
		@DisplayName("실행 중인 잡이 없으면 예상된 파라미터로 잡을 실행하고 감사 로그를 기록한다")
		void launchesJobAndRecordsAudit() throws Exception {
			// given
			given(jobExplorer.findRunningJobExecutions(CatalogImportJobConfig.JOB_NAME)).willReturn(Set.of());
			JobExecution launched = jobExecution(88L, jobInstance(1L),
					new JobParametersBuilder().toJobParameters(), BatchStatus.STARTED);
			given(jobLauncher.run(eq(discogsMasterImportJob), any())).willReturn(launched);
			CatalogImportJobRequest request = new CatalogImportJobRequest(21247L, BigDecimal.valueOf(45000));

			// when
			CatalogImportJobStartResponse response = service().start(1L, request);

			// then
			assertThat(response.jobExecutionId()).isEqualTo(88L);
			ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
			verify(jobLauncher).run(eq(discogsMasterImportJob), captor.capture());
			JobParameters usedParams = captor.getValue();
			assertThat(usedParams.getLong(CatalogImportJobConfig.PARAM_MASTER_ID)).isEqualTo(21247L);
			assertThat(usedParams.getString(CatalogImportJobConfig.PARAM_DEFAULT_PRICE)).isEqualTo("45000");
			assertThat(usedParams.getParameter(CatalogImportJobConfig.PARAM_DEFAULT_PRICE).isIdentifying()).isFalse();
			verify(adminAuditLogService).record(1L, AdminAuditAction.CATALOG_IMPORT_JOB_START,
					AdminAuditTargetType.CATALOG_IMPORT_JOB, 88L, "discogsMasterId=21247");
		}

		@Test
		@DisplayName("잡 실행이 실패하면 CATALOG_IMPORT_JOB_RUNNING 예외를 던진다")
		void throwsWhenJobLauncherFails() throws Exception {
			// given
			given(jobExplorer.findRunningJobExecutions(CatalogImportJobConfig.JOB_NAME)).willReturn(Set.of());
			given(jobLauncher.run(eq(discogsMasterImportJob), any()))
					.willThrow(new JobRestartException("이미 완료된 잡"));
			CatalogImportJobRequest request = new CatalogImportJobRequest(21247L, BigDecimal.valueOf(45000));

			// when & then
			assertThatThrownBy(() -> service().start(1L, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_IMPORT_JOB_RUNNING);
		}
	}

	@Nested
	@DisplayName("list()")
	class ListJobs {

		@Test
		@DisplayName("잡이 한 번도 실행된 적 없으면 빈 페이지를 반환한다")
		void returnsEmptyPageWhenNoSuchJob() throws Exception {
			// given
			given(jobExplorer.getJobInstanceCount(CatalogImportJobConfig.JOB_NAME))
					.willThrow(new NoSuchJobException("no job"));

			// when
			PageResponse<CatalogImportJobResponse> result = service().list(0, 20);

			// then
			assertThat(result.content()).isEmpty();
			assertThat(result.totalElements()).isZero();
		}

		@Test
		@DisplayName("인스턴스별 최신 실행 결과를 매핑한다")
		void mapsLatestExecutionPerInstance() throws Exception {
			// given
			given(jobExplorer.getJobInstanceCount(CatalogImportJobConfig.JOB_NAME)).willReturn(1L);
			JobInstance instance = jobInstance(1L);
			given(jobExplorer.getJobInstances(CatalogImportJobConfig.JOB_NAME, 0, 20)).willReturn(List.of(instance));
			CatalogImportJobHistoryRow row = new CatalogImportJobHistoryRow(1L, 88L, BatchStatus.STARTED, null, null,
					null, 21247L, 5L, 10L, 8L, 1L, 0L, 0L, 0L, null);
			given(catalogImportJobQueryMapper.findLatestExecutions(List.of(1L), CatalogImportJobConfig.PARAM_MASTER_ID))
					.willReturn(List.of(row));

			// when
			PageResponse<CatalogImportJobResponse> result = service().list(0, 20);

			// then
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0).jobExecutionId()).isEqualTo(88L);
			assertThat(result.content().get(0).discogsMasterId()).isEqualTo(21247L);
		}

		@Test
		@DisplayName("실행 이력이 없는 인스턴스는 결과에서 제외한다")
		void skipsInstancesWithNoExecutions() throws Exception {
			// given
			given(jobExplorer.getJobInstanceCount(CatalogImportJobConfig.JOB_NAME)).willReturn(1L);
			JobInstance instance = jobInstance(1L);
			given(jobExplorer.getJobInstances(CatalogImportJobConfig.JOB_NAME, 0, 20)).willReturn(List.of(instance));
			given(catalogImportJobQueryMapper.findLatestExecutions(List.of(1L), CatalogImportJobConfig.PARAM_MASTER_ID))
					.willReturn(List.of());

			// when
			PageResponse<CatalogImportJobResponse> result = service().list(0, 20);

			// then
			assertThat(result.content()).isEmpty();
			assertThat(result.totalElements()).isEqualTo(1L);
		}
	}

	@Nested
	@DisplayName("get()")
	class Get {

		@Test
		@DisplayName("존재하지 않으면 CATALOG_IMPORT_JOB_NOT_FOUND 예외를 던진다")
		void throwsWhenNull() {
			// given
			given(jobExplorer.getJobExecution(999L)).willReturn(null);

			// when & then
			assertThatThrownBy(() -> service().get(999L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_IMPORT_JOB_NOT_FOUND);
		}

		@Test
		@DisplayName("다른 잡 이름의 실행이면 CATALOG_IMPORT_JOB_NOT_FOUND 예외를 던진다")
		void throwsWhenOtherJobName() {
			// given
			JobInstance otherInstance = new JobInstance(1L, "otherJob");
			JobExecution execution = jobExecution(1L, otherInstance,
					new JobParametersBuilder().toJobParameters(), BatchStatus.STARTED);
			given(jobExplorer.getJobExecution(1L)).willReturn(execution);

			// when & then
			assertThatThrownBy(() -> service().get(1L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_IMPORT_JOB_NOT_FOUND);
		}

		@Test
		@DisplayName("존재하면 실행 결과를 매핑해 반환한다")
		void returnsResponseWhenFound() {
			// given
			JobInstance instance = jobInstance(1L);
			JobParameters params = new JobParametersBuilder()
					.addLong(CatalogImportJobConfig.PARAM_MASTER_ID, 21247L)
					.toJobParameters();
			JobExecution execution = jobExecution(88L, instance, params, BatchStatus.COMPLETED);
			given(jobExplorer.getJobExecution(88L)).willReturn(execution);

			// when
			CatalogImportJobResponse response = service().get(88L);

			// then
			assertThat(response.jobExecutionId()).isEqualTo(88L);
			assertThat(response.discogsMasterId()).isEqualTo(21247L);
			assertThat(response.status()).isEqualTo(BatchStatus.COMPLETED);
		}
	}

	@Nested
	@DisplayName("restart()")
	class Restart {

		@Test
		@DisplayName("FAILED 상태가 아니면 COMMON_CONFLICT 예외를 던진다")
		void throwsWhenNotFailed() {
			// given
			JobInstance instance = jobInstance(1L);
			JobExecution execution = jobExecution(1L, instance,
					new JobParametersBuilder().toJobParameters(),
					BatchStatus.COMPLETED);
			given(jobExplorer.getJobExecution(1L)).willReturn(execution);

			// when & then
			assertThatThrownBy(() -> service().restart(1L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_CONFLICT);
		}

		@Test
		@DisplayName("FAILED 상태면 jobOperator 로 재시작을 위임한다")
		void delegatesToJobOperator() throws Exception {
			// given
			JobInstance instance = jobInstance(1L);
			JobExecution execution = jobExecution(1L, instance,
					new JobParametersBuilder().toJobParameters(), BatchStatus.FAILED);
			execution.setExitStatus(ExitStatus.FAILED);
			given(jobExplorer.getJobExecution(1L)).willReturn(execution);
			given(jobOperator.restart(1L)).willReturn(89L);

			// when
			CatalogImportJobStartResponse response = service().restart(1L);

			// then
			assertThat(response.jobExecutionId()).isEqualTo(89L);
			verify(jobOperator).restart(1L);
		}

		@Test
		@DisplayName("jobOperator 재시작이 실패하면 COMMON_CONFLICT 예외를 던진다")
		void throwsWhenJobOperatorFails() throws Exception {
			// given
			JobInstance instance = jobInstance(1L);
			JobExecution execution = jobExecution(1L, instance,
					new JobParametersBuilder().toJobParameters(), BatchStatus.FAILED);
			execution.setExitStatus(ExitStatus.FAILED);
			given(jobExplorer.getJobExecution(1L)).willReturn(execution);
			given(jobOperator.restart(1L)).willThrow(new NoSuchJobExecutionException("실행 이력 없음"));

			// when & then
			assertThatThrownBy(() -> service().restart(1L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_CONFLICT);
		}
	}
}
