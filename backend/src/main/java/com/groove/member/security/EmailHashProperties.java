package com.groove.member.security;

import com.groove.common.config.SecretPlaceholderGuard;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * 이메일 점유 해시(HMAC-SHA256)에 쓰는 서버 전용 비밀키 설정. email.hash.* 키와 1:1 매핑된다. secret 은 최소
 * 32바이트(256bit) 이상이어야 하며, 플레이스홀더 값이면 SecretPlaceholderGuard 가 기동을 거부한다.
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
