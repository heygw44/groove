package com.groove.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시 정책 (ERD §4.1 비고: BCrypt cost 12).
 *
 * <p>{@link SecurityConfig} 와 분리한 이유: SecurityFilterChain 빈이 비활성화돼도
 * 비밀번호 해시는 항상 동일 정책을 따라야 하고, 도메인 서비스(MemberService 등) 가
 * SecurityConfig 에 의존하지 않아야 하기 때문.
 */
@Configuration
public class PasswordConfig {

    /** ERD 명시: cost 12. */
    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
