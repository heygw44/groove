package com.groove.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 통합 테스트용 MySQL·Redis 컨테이너. .withReuse(true) + 정적 싱글턴으로 테스트 클래스 간 컨테이너를 재사용한다.
 * ServiceConnection 으로 DataSource·Flyway·JPA(MySQL)·spring.data.redis(Redis)가 컨테이너에 자동 연결된다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("groove")
            .withUsername("groove")
            .withPassword("test")
            .withReuse(true);

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @Bean
    @ServiceConnection
    public MySQLContainer mysqlContainer() {
        return MYSQL;
    }

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return REDIS;
    }
}
