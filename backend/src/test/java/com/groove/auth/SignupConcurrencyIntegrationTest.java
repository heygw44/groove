package com.groove.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.service.AuthService;
import com.groove.support.IntegrationTestSupport;

/**
 * 같은 이메일 동시 가입은 둘 다 existsByEmail 선검사를 통과할 수 있어 유니크 제약(uk_member_email)이 최후 방어선이다.
 * 타이밍에 기대지 않도록 먼저 INSERT 한 트랜잭션을 커밋 직전에 붙잡아 두고, 늦은 요청이 그 행의 락을 기다리는
 * 것(LOCK WAIT)을 확인한 뒤에 커밋시켜 늦은 요청이 반드시 중복키 위반 경로를 타게 만든다.
 */
@AutoConfigureMockMvc
class SignupConcurrencyIntegrationTest extends IntegrationTestSupport {

	private static final long TIMEOUT_SECONDS = 10;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	AuthService authService;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void grantDataLocksPrivilege() throws Exception {
		String url;
		try (Connection appConnection = jdbcTemplate.getDataSource().getConnection()) {
			url = appConnection.getMetaData().getURL();
		}
		// performance_schema.data_locks 조회 권한. PROCESS 같은 전역 권한은 새 커넥션에만 반영돼 풀에 이미 열린
		// 커넥션으로는 조회가 막히므로, 문장마다 검사되는 DB 단위 권한을 쓴다.
		// IntegrationTestSupport 의 MySQLContainer 는 root 패스워드를 app 유저 패스워드와 동일하게 맞춘다.
		try (Connection rootConnection = DriverManager.getConnection(url, "root", "groove1234")) {
			rootConnection.createStatement().execute("GRANT SELECT ON performance_schema.* TO 'groove'@'%'");
			rootConnection.createStatement().execute("FLUSH PRIVILEGES");
		}
	}

	@Nested
	@DisplayName("POST /api/v1/auth/signup")
	class Signup {

		@Test
		@DisplayName("같은 이메일로 동시에 가입하면 늦게 INSERT 한 요청이 MEMBER_EMAIL_DUPLICATE 를 받는다")
		void returnsEmailDuplicateWhenConcurrentInsertLoses() throws Exception {
			// given
			String email = "race-" + UUID.randomUUID() + "@groove.com";
			SignupRequest request = new SignupRequest(email, "password1", "그루버");
			CountDownLatch firstInserted = new CountDownLatch(1);
			CountDownLatch releaseFirst = new CountDownLatch(1);
			TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
			ExecutorService executor = Executors.newFixedThreadPool(2);

			try {
				Future<?> first = executor.submit(() -> txTemplate.executeWithoutResult(status -> {
					authService.signup(request);
					firstInserted.countDown();
					try {
						releaseFirst.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}));
				assertThat(firstInserted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

				// when
				Future<MvcResult> second = executor.submit(() -> mockMvc.perform(post("/api/v1/auth/signup")
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(request)))
						.andReturn());
				boolean secondWaiting = awaitMemberInsertLockWait();
				releaseFirst.countDown();
				first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
				MvcResult result = second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

				// then
				assertThat(secondWaiting).isTrue();
				assertThat(result.getResponse().getStatus()).isEqualTo(409);
				String errorCode = objectMapper.readTree(result.getResponse().getContentAsString())
						.path("error").path("code").asText();
				assertThat(errorCode).isEqualTo("MEMBER_EMAIL_DUPLICATE");
				Integer memberCount = jdbcTemplate.queryForObject(
						"select count(*) from member where email = ?", Integer.class, email);
				assertThat(memberCount).isEqualTo(1);
			} finally {
				releaseFirst.countDown();
				executor.shutdownNow();
				executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			}
		}
	}

	/** 늦은 요청이 선검사를 통과해 member INSERT 에서 먼저 들어간 행의 락을 기다리기 시작할 때까지 기다린다. */
	private boolean awaitMemberInsertLockWait() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
		while (System.nanoTime() < deadline) {
			Integer waiting = jdbcTemplate.queryForObject(
					"select count(*) from performance_schema.data_locks "
							+ "where OBJECT_SCHEMA = DATABASE() and OBJECT_NAME = 'member' "
							+ "and LOCK_STATUS = 'WAITING'",
					Integer.class);
			if (waiting != null && waiting > 0) {
				return true;
			}
			Thread.sleep(50);
		}
		return false;
	}
}
