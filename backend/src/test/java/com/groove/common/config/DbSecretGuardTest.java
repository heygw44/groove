package com.groove.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DbSecretGuard — prod DB 시크릿 약한값 거부")
class DbSecretGuardTest {

    private static DbSecretGuard guard(MockEnvironment env) {
        return new DbSecretGuard(env);
    }

    @Test
    @DisplayName("약한 DB_PASSWORD 면 기동을 거부한다")
    void rejectsWeakDbPassword() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "rootpw");
        assertThatThrownBy(() -> guard(env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    @DisplayName("DB_PASSWORD 가 빈 문자열이면 거부한다 (미설정은 base 의 폴백 없는 ${DB_PASSWORD} 가 별도로 차단)")
    void rejectsBlankDbPassword() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "");
        assertThatThrownBy(() -> guard(env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    @DisplayName("약한 MYSQL_ROOT_PASSWORD 가 존재하면 거부한다")
    void rejectsWeakRootPassword() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "kP9$mVx2Lr8QwZ7nTb4Hy6Fc")
                .withProperty("MYSQL_ROOT_PASSWORD", "rootpw");
        assertThatThrownBy(() -> guard(env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MYSQL_ROOT_PASSWORD");
    }

    @Test
    @DisplayName("MYSQL_ROOT_PASSWORD 가 존재하지만 빈 값이면 거부한다 (게이트 우회 방지)")
    void rejectsBlankRootPassword() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "kP9$mVx2Lr8QwZ7nTb4Hy6Fc")
                .withProperty("MYSQL_ROOT_PASSWORD", "");
        assertThatThrownBy(() -> guard(env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MYSQL_ROOT_PASSWORD");
    }

    @Test
    @DisplayName("강한 DB_PASSWORD + MYSQL_ROOT_PASSWORD 부재면 통과한다 (관리형 DB)")
    void allowsStrongPasswordWithoutRoot() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "kP9$mVx2Lr8QwZ7nTb4Hy6Fc");
        assertThatCode(() -> guard(env).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("강한 DB_PASSWORD + 강한 MYSQL_ROOT_PASSWORD 면 통과한다")
    void allowsStrongPasswordAndRoot() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "kP9$mVx2Lr8QwZ7nTb4Hy6Fc")
                .withProperty("MYSQL_ROOT_PASSWORD", "Rb7!nQ4wZ2tLp9Vx6Hy0Fc3");
        assertThatCode(() -> guard(env).afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
