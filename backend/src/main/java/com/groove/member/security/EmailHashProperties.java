package com.groove.member.security;

import com.groove.common.config.SecretPlaceholderGuard;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * 이메일 점유 해시(HMAC-SHA256)에 쓰는 서버 전용 비밀키 설정 (#186).
 *
 * <p>{@code application.yaml} 의 {@code email.hash.*} 키와 1:1 매핑된다. JWT 서명키
 * ({@link com.groove.auth.security.JwtProperties}) 와 <b>의도적으로 분리</b>한다 — 키 1개=용도 1개
 * (key separation, NIST SP 800-57). JWT 시크릿 롤링이 이메일 점유 해시를 깨뜨리면 안 되기 때문이다.
 * compact constructor 에서 검증하므로 잘못된 운영 설정은 빈 생성 시점에 즉시 실패한다.
 * {@link #secret} 은 HMAC-SHA256 키이므로 최소 32바이트(256bit) 이상이어야 하며,
 * {@code .env.example} 의 플레이스홀더 값이면 {@link SecretPlaceholderGuard} 가 기동을 거부한다(이슈 #165).
 */
@ConfigurationProperties(prefix = "email.hash")
public record EmailHashProperties(String secret) {

    private static final int MIN_SECRET_BYTES = 32;

    public EmailHashProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "email.hash.secret 은 UTF-8 기준 최소 " + MIN_SECRET_BYTES + " 바이트(256bit) 이상이어야 합니다");
        }
        SecretPlaceholderGuard.rejectPlaceholder("email.hash.secret", secret);
    }
}
