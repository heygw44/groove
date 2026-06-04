package com.groove.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * JWT 발급/검증에 필요한 비밀 키와 TTL 설정.
 *
 * <p>{@code application.yaml}의 {@code jwt.*} 키와 1:1 매핑된다. compact constructor 에서
 * 검증하므로 잘못된 운영 설정은 빈 생성 시점에 즉시 실패한다.
 * {@link #secret} 은 HS256 서명에 사용되므로 최소 32바이트(256bit) 이상이어야 한다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds
) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 은 UTF-8 기준 최소 " + MIN_SECRET_BYTES + " 바이트(256bit) 이상이어야 합니다");
        }
        if (accessTokenTtlSeconds <= 0) {
            throw new IllegalStateException("jwt.access-token-ttl-seconds 는 양수여야 합니다");
        }
        if (refreshTokenTtlSeconds <= 0) {
            throw new IllegalStateException("jwt.refresh-token-ttl-seconds 는 양수여야 합니다");
        }
    }
}
