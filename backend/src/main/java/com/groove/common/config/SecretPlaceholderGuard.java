package com.groove.common.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * .env.example 의 플레이스홀더 시크릿으로 기동하는 것을 감지하면 IllegalStateException 으로 기동을 중단하는 가드.
 *
 * 판정은 두 단계 — (1) .env.example 동봉 플레이스홀더 정확 일치, (2) 교체 의도 마커
 * 부분문자열(change-this/change-me/changeme, 대소문자 무시).
 *
 * null/blank/길이 검증은 호출측 책임이며, rejectPlaceholder 진입 시점의 secret 은 non-null 을 전제한다.
 */
public final class SecretPlaceholderGuard {

    /** .env.example 에 동봉된 플레이스홀더 (정확 일치, 소문자). */
    private static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "change-this-to-a-256-bit-secret-key-in-production",
            "change-this-mock-webhook-secret-in-production",
            "change-this-to-a-256-bit-email-hash-key-in-production"
    );

    /** 교체 의도 마커 (부분문자열, 대소문자 무시). */
    private static final List<String> MARKERS = List.of("change-this", "change-me", "changeme");

    private SecretPlaceholderGuard() {
    }

    /**
     * 시크릿이 .env.example 플레이스홀더로 판단되면 IllegalStateException 을 던진다.
     * propertyName=오류 메시지에 노출할 설정 키, secret=검사 대상 시크릿(non-null 전제).
     */
    public static void rejectPlaceholder(String propertyName, String secret) {
        String normalized = secret.strip().toLowerCase(Locale.ROOT);
        boolean isPlaceholder = KNOWN_PLACEHOLDERS.contains(normalized)
                || MARKERS.stream().anyMatch(normalized::contains);
        if (isPlaceholder) {
            throw new IllegalStateException(
                    propertyName + " 가 .env.example 의 플레이스홀더 값입니다 — 운영 배포 전 고유한 시크릿으로 반드시 교체하세요 (이슈 #165)");
        }
    }
}
