package com.groove.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 공통 베이스. MySQL/Redis를 Testcontainers로 띄우고
 * @ServiceConnection 으로 datasource / redis 접속 정보를 자동 주입한다.
 * 컨테이너는 static 이라 테스트 클래스 간 재사용된다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
public abstract class IntegrationTestSupport {

	@Container
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
			.withDatabaseName("groove")
			.withUsername("groove")
			.withPassword("groove1234")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

	@Container
	@ServiceConnection(name = "redis")
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);
}
