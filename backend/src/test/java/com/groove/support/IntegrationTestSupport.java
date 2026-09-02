package com.groove.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 공통 베이스. MySQL/Redis를 Testcontainers로 띄우고
 * @ServiceConnection 으로 datasource / redis 접속 정보를 자동 주입한다.
 * 컨테이너는 {@code @Container} 대신 수동으로 한 번만 띄운다. {@code @Container} 는 클래스 단위로 컨테이너를
 * 재기동해 캐시된 Spring 컨텍스트가 (동일 시그니처의) 다른 테스트 클래스에서 재사용될 때 접속 포트가 어긋난다.
 */
@ActiveProfiles("test")
@SpringBootTest
public abstract class IntegrationTestSupport {

	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
			.withDatabaseName("groove")
			.withUsername("groove")
			.withPassword("groove1234")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

	@ServiceConnection(name = "redis")
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	static {
		MYSQL.start();
		REDIS.start();
	}
}
