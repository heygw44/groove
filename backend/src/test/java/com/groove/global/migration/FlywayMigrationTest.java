package com.groove.global.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 운영 배포의 안전장치. 빈 MySQL 에 Flyway 마이그레이션만 적용한 뒤 ddl-auto: validate 로 컨텍스트를 띄운다.
 * 엔티티를 고치고 마이그레이션을 빼먹으면 여기서 먼저 깨진다.
 * IntegrationTestSupport 의 공용 컨테이너는 create-drop 으로 이미 스키마가 올라와 있어 검증이 성립하지 않으므로
 * 전용 컨테이너를 따로 띄운다.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
	"spring.flyway.enabled=true",
	"spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayMigrationTest {

	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
			.withDatabaseName("groove_migration")
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

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Nested
	@DisplayName("migrate()")
	class Migrate {

		@Test
		@DisplayName("빈 데이터베이스에 마이그레이션을 적용하면 모든 버전이 성공으로 기록된다")
		void recordsEverySuccessfulVersion() {
			// when
			Integer failed = jdbcTemplate.queryForObject(
					"select count(*) from flyway_schema_history where success = 0", Integer.class);
			Integer applied = jdbcTemplate.queryForObject(
					"select count(*) from flyway_schema_history where success = 1", Integer.class);

			// then
			assertThat(failed).isZero();
			assertThat(applied).isPositive();
		}

		@Test
		@DisplayName("마이그레이션으로 만든 스키마는 엔티티 매핑 검증(validate)을 통과한다")
		void matchesEntityMapping() {
			// given & when
			// 컨텍스트가 ddl-auto: validate 로 기동된 것 자체가 검증이다. 스키마가 실제로 있는지만 확인한다.
			Integer tables = jdbcTemplate.queryForObject(
					"select count(*) from information_schema.tables where table_schema = database()", Integer.class);

			// then
			assertThat(tables).isGreaterThan(20);
		}
	}
}
