package com.groove.common.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * .env.example 의 플레이스홀더 시크릿을 그대로 복붙해 배포하는 실수를 기동 시점에 차단하는 fail-fast 가드 (이슈 #165).
 *
 * 길이 검증만 하던 jwt.secret(JwtProperties)과 null/blank 검증만 하던 payment.mock.webhook-secret(PaymentMockProperties)은
 * 값 자체를 보지 않아, .env.example 의 플레이스홀더(예: change-this-to-a-256-bit-secret-key-in-production, 49바이트)가
 * 검증을 통과한 채 기동됐다. 공개된 시크릿으로 기동되면 공격자가 임의 role:ADMIN JWT 를 위조할 수 있어, 이 가드는
 * 플레이스홀더를 감지하면 IllegalStateException 으로 기동을 중단한다(ProductionSeedGuard/JwtProperties 와 동일한 fail-fast).
 *
 * 판정은 두 단계 — (1) .env.example 동봉 플레이스홀더 정확 일치, (2) "교체하라"는 의도를 드러내는 마커
 * 부분문자열(change-this/change-me/changeme, 대소문자 무시). local/test 프로파일의 더미 시크릿(local-dev-*, test-*)은
 * 마커를 포함하지 않으므로 영향받지 않는다 — 따라서 프로파일 분기 없이 전 프로파일에서 무조건 검사한다.
 *
 * null/blank/길이 검증은 호출측 책임이다. 이 가드는 플레이스홀더 판정만 하며, rejectPlaceholder 진입 시점의
 * secret 은 non-null 을 전제한다.
 */
public final class SecretPlaceholderGuard {

    /** .env.example 에 동봉된 플레이스홀더 (정확 일치, 소문자). */
    private static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "change-this-to-a-256-bit-secret-key-in-production",
            "change-this-mock-webhook-secret-in-production",
            "change-this-to-a-256-bit-email-hash-key-in-production"
    );

    /** "교체하라"는 의도를 드러내는 마커 (부분문자열, 대소문자 무시). */
    private static final List<String> MARKERS = List.of("change-this", "change-me", "changeme");

    private SecretPlaceholderGuard() {
    }

    /**
     * 시크릿이 .env.example 플레이스홀더로 판단되면 IllegalStateException 을 던진다.
     *
     * @param propertyName 오류 메시지에 노출할 설정 키 (예: "jwt.secret")
     * @param secret       검사 대상 시크릿 (non-null 전제 — null/blank/길이 검증은 호출측 책임)
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
