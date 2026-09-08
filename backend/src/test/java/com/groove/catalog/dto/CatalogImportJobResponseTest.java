package com.groove.catalog.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;

import com.groove.catalog.batch.CatalogImportJobConfig;

class CatalogImportJobResponseTest {

	@Nested
	@DisplayName("from()")
	class From {

		@Test
		@DisplayName("스텝 카운트를 합산하고 스텝 종료 메시지를 사용한다")
		void sumsStepCountsAndUsesStepExitDescription() {
			// given
			JobInstance instance = new JobInstance(1L, CatalogImportJobConfig.JOB_NAME);
			JobExecution execution = new JobExecution(instance, 88L, new JobParametersBuilder()
					.addLong(CatalogImportJobConfig.PARAM_MASTER_ID, 21247L)
					.toJobParameters());
			execution.setStatus(BatchStatus.FAILED);

			StepExecution step1 = execution.createStepExecution("step1");
			step1.setReadCount(10);
			step1.setWriteCount(8);
			step1.setReadSkipCount(1);
			step1.setFilterCount(1);
			step1.setExitStatus(ExitStatus.COMPLETED);

			StepExecution step2 = execution.createStepExecution("step2");
			step2.setReadCount(2);
			step2.setWriteCount(1);
			step2.setReadSkipCount(0);
			step2.setFilterCount(0);
			step2.setExitStatus(new ExitStatus("FAILED", "릴리즈 조회 실패"));

			// when
			CatalogImportJobResponse response = CatalogImportJobResponse.from(execution);

			// then
			assertThat(response.jobExecutionId()).isEqualTo(88L);
			assertThat(response.discogsMasterId()).isEqualTo(21247L);
			assertThat(response.readCount()).isEqualTo(12);
			assertThat(response.writeCount()).isEqualTo(9);
			assertThat(response.skipCount()).isEqualTo(1);
			assertThat(response.filterCount()).isEqualTo(1);
			assertThat(response.exitMessage()).isEqualTo("릴리즈 조회 실패");
		}

		@Test
		@DisplayName("긴 종료 메시지는 1000자로 자른다")
		void truncatesLongExitMessage() {
			// given
			JobInstance instance = new JobInstance(1L, CatalogImportJobConfig.JOB_NAME);
			JobExecution execution = new JobExecution(instance, 88L, new JobParametersBuilder()
					.addLong(CatalogImportJobConfig.PARAM_MASTER_ID, 21247L)
					.toJobParameters());
			execution.setStatus(BatchStatus.FAILED);
			String longMessage = "e".repeat(2000);
			execution.setExitStatus(new ExitStatus("FAILED", longMessage));

			// when
			CatalogImportJobResponse response = CatalogImportJobResponse.from(execution);

			// then
			assertThat(response.exitMessage()).hasSize(1000);
		}
	}

	@Nested
	@DisplayName("fromRows()")
	class FromRows {

		@Test
		@DisplayName("스텝 행을 합산하고 스텝 실행 id 순으로 첫 종료 메시지를 사용한다")
		void sumsStepRowsAndUsesFirstStepExitMessage() {
			// given
			CatalogImportJobHistoryRow step1 = new CatalogImportJobHistoryRow(1L, 88L, BatchStatus.FAILED, null, null,
					"잡 종료 메시지", 21247L, 10L, 10L, 8L, 1L, 1L, 0L, 0L, "");
			CatalogImportJobHistoryRow step2 = new CatalogImportJobHistoryRow(1L, 88L, BatchStatus.FAILED, null, null,
					"잡 종료 메시지", 21247L, 11L, 2L, 1L, 0L, 0L, 0L, 0L, "릴리즈 조회 실패");

			// when
			CatalogImportJobResponse response = CatalogImportJobResponse.fromRows(List.of(step1, step2));

			// then
			assertThat(response.jobExecutionId()).isEqualTo(88L);
			assertThat(response.discogsMasterId()).isEqualTo(21247L);
			assertThat(response.readCount()).isEqualTo(12);
			assertThat(response.writeCount()).isEqualTo(9);
			assertThat(response.skipCount()).isEqualTo(1);
			assertThat(response.filterCount()).isEqualTo(1);
			assertThat(response.exitMessage()).isEqualTo("릴리즈 조회 실패");
		}

		@Test
		@DisplayName("스텝 종료 메시지가 전부 없으면 잡 종료 메시지를 사용한다")
		void fallsBackToJobExitMessageWhenStepsHaveNone() {
			// given
			CatalogImportJobHistoryRow step = new CatalogImportJobHistoryRow(1L, 88L, BatchStatus.COMPLETED, null,
					null, "잡 종료 메시지", 21247L, 10L, 10L, 8L, 0L, 0L, 0L, 0L, null);

			// when
			CatalogImportJobResponse response = CatalogImportJobResponse.fromRows(List.of(step));

			// then
			assertThat(response.exitMessage()).isEqualTo("잡 종료 메시지");
		}

		@Test
		@DisplayName("실행 이력에 스텝이 없으면 카운트는 0이다")
		void treatsMissingStepAsZeroCounts() {
			// given: 스텝 없는 실행은 LEFT JOIN 결과로 stepExecutionId 가 null 인 행 한 건이 온다
			CatalogImportJobHistoryRow noStepRow = new CatalogImportJobHistoryRow(1L, 88L, BatchStatus.STARTED, null,
					null, null, 21247L, null, null, null, null, null, null, null, null);

			// when
			CatalogImportJobResponse response = CatalogImportJobResponse.fromRows(List.of(noStepRow));

			// then
			assertThat(response.readCount()).isZero();
			assertThat(response.writeCount()).isZero();
			assertThat(response.skipCount()).isZero();
			assertThat(response.filterCount()).isZero();
			assertThat(response.exitMessage()).isNull();
		}

		@Test
		@DisplayName("긴 종료 메시지는 1000자로 자른다")
		void truncatesLongExitMessage() {
			// given
			String longMessage = "e".repeat(2000);
			CatalogImportJobHistoryRow step = new CatalogImportJobHistoryRow(1L, 88L, BatchStatus.FAILED, null, null,
					null, 21247L, 10L, 0L, 0L, 0L, 0L, 0L, 0L, longMessage);

			// when
			CatalogImportJobResponse response = CatalogImportJobResponse.fromRows(List.of(step));

			// then
			assertThat(response.exitMessage()).hasSize(1000);
		}
	}
}
