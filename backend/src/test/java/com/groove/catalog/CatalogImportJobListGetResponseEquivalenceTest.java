package com.groove.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.groove.catalog.batch.CatalogImportJobConfig;
import com.groove.catalog.dto.CatalogImportJobResponse;
import com.groove.catalog.service.CatalogImportJobService;
import com.groove.global.common.PageResponse;
import com.groove.support.IntegrationTestSupport;

/**
 * {@code list()}(매퍼 경로)와 {@code get()}(JobExplorer 경로)가 같은 실행에 대해 필드 단위로 완전히 같은 응답을
 * 만드는지 감시한다. 두 경로가 갈라지는 것이 매퍼 도입의 가장 큰 위험이라 별도 회귀 테스트로 고정한다.
 */
class CatalogImportJobListGetResponseEquivalenceTest extends IntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CatalogImportJobService catalogImportJobService;

	private final AtomicLong idSeq = new AtomicLong(1);

	@Test
	@DisplayName("스텝 2개에 스킵 3종이 모두 0이 아니면 list()와 get() 응답이 같다")
	void matchesWhenStepsHaveAllSkipTypes() {
		// given
		long instanceId = insertJobInstance(2000L);
		long executionId = insertExecution(instanceId, 21247L, "COMPLETED", "COMPLETED", null);
		insertStepExecution(executionId, "step1", 10, 8, 1, 2, 0, 1, "");
		insertStepExecution(executionId, "step2", 5, 4, 0, 0, 3, 0, "");

		// when & then
		assertResponsesMatch(instanceId, executionId);
	}

	@Test
	@DisplayName("종료 메시지가 스텝에만 있으면 list()와 get() 응답이 같다")
	void matchesWhenExitMessageOnlyOnStep() {
		// given
		long instanceId = insertJobInstance(2001L);
		long executionId = insertExecution(instanceId, 21248L, "FAILED", "FAILED", null);
		insertStepExecution(executionId, "step1", 10, 8, 0, 0, 0, 0, "릴리즈 조회 실패");

		// when & then
		assertResponsesMatch(instanceId, executionId);
	}

	@Test
	@DisplayName("종료 메시지가 잡에만 있으면 list()와 get() 응답이 같다")
	void matchesWhenExitMessageOnlyOnJob() {
		// given
		long instanceId = insertJobInstance(2002L);
		long executionId = insertExecution(instanceId, 21249L, "FAILED", "FAILED", "파라미터 검증 실패");
		insertStepExecution(executionId, "step1", 10, 8, 0, 0, 0, 0, "");

		// when & then
		assertResponsesMatch(instanceId, executionId);
	}

	@Test
	@DisplayName("실행이 2건(재시작)인 인스턴스는 최신 실행 기준으로 list()와 get() 응답이 같다")
	void matchesWhenInstanceWasRestarted() {
		// given
		long instanceId = insertJobInstance(2003L);
		long firstExecutionId = insertExecution(instanceId, 21250L, "FAILED", "FAILED", "첫 실행 실패");
		insertStepExecution(firstExecutionId, "step1", 3, 2, 1, 0, 0, 0, "");
		long secondExecutionId = insertExecution(instanceId, 21250L, "COMPLETED", "COMPLETED", null);
		insertStepExecution(secondExecutionId, "step1", 10, 8, 0, 0, 0, 0, "");

		// when & then
		assertResponsesMatch(instanceId, secondExecutionId);
	}

	private void assertResponsesMatch(long instanceId, long expectedLatestExecutionId) {
		PageResponse<CatalogImportJobResponse> page = catalogImportJobService.list(0, 20);
		CatalogImportJobResponse fromList = page.content().stream()
				.filter(response -> response.jobExecutionId().equals(expectedLatestExecutionId))
				.findFirst()
				.orElseThrow();
		CatalogImportJobResponse fromGet = catalogImportJobService.get(expectedLatestExecutionId);

		assertThat(fromList).isEqualTo(fromGet);
	}

	private long insertJobInstance(long instanceId) {
		String jobKey = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
		jdbcTemplate.update(
				"INSERT INTO BATCH_JOB_INSTANCE (JOB_INSTANCE_ID, VERSION, JOB_NAME, JOB_KEY) VALUES (?, 0, ?, ?)",
				instanceId, CatalogImportJobConfig.JOB_NAME, jobKey);
		return instanceId;
	}

	private long insertExecution(long instanceId, long masterId, String status, String exitCode,
			String jobExitMessage) {
		long executionId = idSeq.getAndIncrement() + instanceId * 100;
		LocalDateTime now = LocalDateTime.now();
		jdbcTemplate.update("""
				INSERT INTO BATCH_JOB_EXECUTION
					(JOB_EXECUTION_ID, VERSION, JOB_INSTANCE_ID, CREATE_TIME, START_TIME, END_TIME, STATUS,
					EXIT_CODE, EXIT_MESSAGE, LAST_UPDATED)
				VALUES (?, 0, ?, ?, ?, ?, ?, ?, ?, ?)
				""", executionId, instanceId, now, now, now, status, exitCode, jobExitMessage, now);

		jdbcTemplate.update("""
				INSERT INTO BATCH_JOB_EXECUTION_PARAMS
					(JOB_EXECUTION_ID, PARAMETER_NAME, PARAMETER_TYPE, PARAMETER_VALUE, IDENTIFYING)
				VALUES (?, 'discogsMasterId', 'java.lang.Long', ?, 'Y')
				""", executionId, String.valueOf(masterId));
		jdbcTemplate.update("""
				INSERT INTO BATCH_JOB_EXECUTION_PARAMS
					(JOB_EXECUTION_ID, PARAMETER_NAME, PARAMETER_TYPE, PARAMETER_VALUE, IDENTIFYING)
				VALUES (?, 'defaultPrice', 'java.lang.String', '10000', 'N')
				""", executionId);
		return executionId;
	}

	private void insertStepExecution(long executionId, String stepName, long readCount, long writeCount,
			long readSkipCount, long writeSkipCount, long processSkipCount, long filterCount, String exitMessage) {
		long stepExecutionId = idSeq.getAndIncrement() + executionId * 10;
		LocalDateTime now = LocalDateTime.now();
		jdbcTemplate.update("""
				INSERT INTO BATCH_STEP_EXECUTION
					(STEP_EXECUTION_ID, VERSION, STEP_NAME, JOB_EXECUTION_ID, CREATE_TIME, START_TIME, END_TIME,
					STATUS, COMMIT_COUNT, READ_COUNT, FILTER_COUNT, WRITE_COUNT, READ_SKIP_COUNT,
					WRITE_SKIP_COUNT, PROCESS_SKIP_COUNT, ROLLBACK_COUNT, EXIT_CODE, EXIT_MESSAGE, LAST_UPDATED)
				VALUES (?, 0, ?, ?, ?, ?, ?, 'COMPLETED', 1, ?, ?, ?, ?, ?, ?, 1, '', ?, ?)
				""", stepExecutionId, stepName, executionId, now, now, now, readCount, filterCount, writeCount,
				readSkipCount, writeSkipCount, processSkipCount, exitMessage, now);
	}
}
