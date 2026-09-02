package com.groove.support;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.groove.global.config.JpaConfig;

/**
 * 리포지토리 슬라이스 테스트 공통 베이스. {@code @DataJpaTest} 는 {@code @Configuration} 을 스캔하지 않아
 * JPA Auditing 이 꺼지므로 {@link JpaConfig} 를 직접 import 한다.
 * 컨테이너는 {@code @Container} 대신 수동으로 한 번만 띄운다. 캐시된 Spring 컨텍스트가 여러 테스트 클래스에
 * 걸쳐 재사용되는데, {@code @Container} 는 클래스 단위로 컨테이너를 내려 다음 클래스에서 커넥션이 끊긴다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
public abstract class DataJpaTestSupport {

	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
			.withDatabaseName("groove")
			.withUsername("groove")
			.withPassword("groove1234")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

	static {
		MYSQL.start();
	}
}
