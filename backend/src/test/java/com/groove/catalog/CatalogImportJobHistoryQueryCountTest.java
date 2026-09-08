package com.groove.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.groove.catalog.batch.CatalogImportJobConfig;
import com.groove.catalog.service.CatalogImportJobService;
import com.groove.global.common.PageResponse;
import com.groove.support.IntegrationTestSupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * {@code CatalogImportJobService.list()} 가 인스턴스 수(N)에 관계없이 고정된 수의 SQL 만 실행하는지 감시한다.
 * JobExplorer 는 JdbcTemplate 기반이라 Hibernate Statistics 로는 잡히지 않아, JdbcTemplate 로거에
 * ListAppender 를 붙여 "Executing prepared SQL query"/"Executing prepared SQL statement" 로그 쌍을 센다.
 */
class CatalogImportJobHistoryQueryCountTest extends IntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CatalogImportJobService catalogImportJobService;

	private final AtomicLong idSeq = new AtomicLong(1);

	@Nested
	@DisplayName("list()")
	class ListJobs {

		@Test
		@DisplayName("인스턴스가 늘어도 SQL 실행 수가 그대로다")
		void keepsSqlCountWhenInstanceCountGrows() {
			// given & when
			int sqlCountForOne = measureListSqlCount(1);
			int sqlCountForTwenty = measureListSqlCount(20);

			// then
			assertThat(sqlCountForTwenty).isEqualTo(sqlCountForOne);
		}
	}

	private int measureListSqlCount(int instanceCount) {
		clearBatchTables();
		idSeq.set(1);

		for (int i = 0; i < instanceCount; i++) {
			boolean withRestart = i == instanceCount - 1;
			insertJobInstance(1000L + i, 5000L + i, withRestart);
		}

		Logger jdbcLogger = (Logger) LoggerFactory.getLogger("org.springframework.jdbc.core.JdbcTemplate");
		Level originalLevel = jdbcLogger.getLevel();
		jdbcLogger.setLevel(Level.DEBUG);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		jdbcLogger.addAppender(appender);

		PageResponse<?> page = catalogImportJobService.list(0, 20);

		jdbcLogger.detachAppender(appender);
		jdbcLogger.setLevel(originalLevel);
		appender.stop();

		assertThat(page.content()).hasSize(instanceCount);
		return appender.list.size();
	}

	private void clearBatchTables() {
		jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
		jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION");
		jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
		jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
		jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION");
		jdbcTemplate.update("DELETE FROM BATCH_JOB_INSTANCE");
	}

	private void insertJobInstance(long instanceId, long masterId, boolean withRestart) {
		String jobKey = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
		jdbcTemplate.update(
				"INSERT INTO BATCH_JOB_INSTANCE (JOB_INSTANCE_ID, VERSION, JOB_NAME, JOB_KEY) VALUES (?, 0, ?, ?)",
				instanceId, CatalogImportJobConfig.JOB_NAME, jobKey);

		long firstExecutionId = idSeq.getAndIncrement();
		insertExecution(firstExecutionId, instanceId, masterId, withRestart ? "FAILED" : "COMPLETED",
				withRestart ? "FAILED" : "COMPLETED");
		insertStepExecution(idSeq.getAndIncrement(), firstExecutionId);

		if (withRestart) {
			long secondExecutionId = idSeq.getAndIncrement();
			insertExecution(secondExecutionId, instanceId, masterId, "COMPLETED", "COMPLETED");
			insertStepExecution(idSeq.getAndIncrement(), secondExecutionId);
		}
	}

	private void insertExecution(long executionId, long instanceId, long masterId, String status, String exitCode) {
		LocalDateTime now = LocalDateTime.now();
		jdbcTemplate.update("""
				INSERT INTO BATCH_JOB_EXECUTION
					(JOB_EXECUTION_ID, VERSION, JOB_INSTANCE_ID, CREATE_TIME, START_TIME, END_TIME, STATUS,
					EXIT_CODE, EXIT_MESSAGE, LAST_UPDATED)
				VALUES (?, 0, ?, ?, ?, ?, ?, ?, '', ?)
				""", executionId, instanceId, now, now, now, status, exitCode, now);

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
	}

	private void insertStepExecution(long stepExecutionId, long executionId) {
		LocalDateTime now = LocalDateTime.now();
		jdbcTemplate.update("""
				INSERT INTO BATCH_STEP_EXECUTION
					(STEP_EXECUTION_ID, VERSION, STEP_NAME, JOB_EXECUTION_ID, CREATE_TIME, START_TIME, END_TIME,
					STATUS, COMMIT_COUNT, READ_COUNT, FILTER_COUNT, WRITE_COUNT, READ_SKIP_COUNT,
					WRITE_SKIP_COUNT, PROCESS_SKIP_COUNT, ROLLBACK_COUNT, EXIT_CODE, EXIT_MESSAGE, LAST_UPDATED)
				VALUES (?, 0, ?, ?, ?, ?, ?, 'COMPLETED', 1, 10, 1, 8, 0, 0, 1, 0, 'COMPLETED', '', ?)
				""", stepExecutionId, CatalogImportJobConfig.STEP_NAME, executionId, now, now, now, now);
	}
}
