package com.groove.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

/**
 * 통합 테스트용 MySQL 컨테이너.
 *
 * <p>도커 데몬이 떠 있는 환경에서 자동 기동된다. {@code .withReuse(true)} + 정적 싱글턴으로
 * 테스트 클래스 간 컨테이너를 재사용해 부팅 비용을 줄인다 (~/.testcontainers.properties 의
 * {@code testcontainers.reuse.enable=true} 설정 시 다음 빌드에서도 재사용).
 *
 * <p>Spring Boot 4 의 {@link ServiceConnection} 으로 DataSource·Flyway·JPA 가
 * 컨테이너에 자동 연결된다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("groove")
            .withUsername("groove")
            .withPassword("test")
            .withReuse(true);

    static {
        MYSQL.start();
    }

    @Bean
    @ServiceConnection
    public MySQLContainer<?> mysqlContainer() {
        return MYSQL;
    }
}
