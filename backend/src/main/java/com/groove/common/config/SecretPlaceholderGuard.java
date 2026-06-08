package com.groove.common.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code .env.example} 의 플레이스홀더 시크릿을 그대로 복붙해 배포하는 실수를 기동 시점에 차단하는
 * fail-fast 가드 (이슈 #165).
 *
 * <p>길이 검증만 하던 {@code jwt.secret}({@link com.groove.auth.security.JwtProperties}) 과
 * null/blank 검증만 하던 {@code payment.mock.webhook-secret}
 * ({@link com.groove.payment.gateway.PaymentMockProperties}) 는 값 자체를 보지 않아,
 * {@code .env.example} 의 플레이스홀더(예: {@code change-this-to-a-256-bit-secret-key-in-production},
 * 49바이트)가 검증을 통과한 채 기동됐다. 공개된 시크릿으로 기동되면 공격자가 임의 {@code role:ADMIN}
 * JWT 를 위조할 수 있어, 이 가드는 플레이스홀더를 감지하면 {@link IllegalStateException} 으로
 * <b>기동을 중단</b>한다({@code ProductionSeedGuard}/{@code JwtProperties} 와 동일한 fail-fast).
 *
 * <p>판정은 두 단계다 — (1) {@code .env.example} 동봉 플레이스홀더 <b>정확 일치</b>,
 * (2) "교체하라"는 의도를 드러내는 <b>마커 부분문자열</b>({@code change-this}/{@code change-me}/{@code changeme},
 * 대소문자 무시). local/test 프로파일의 더미 시크릿({@code local-dev-*}, {@code test-*})은 마커를
 * 포함하지 않으므로 영향받지 않는다 — 따라서 프로파일 분기 없이 전 프로파일에서 무조건 검사한다.
 *
 * <p>null/blank/길이 검증은 <b>호출측 책임</b>이다. 이 가드는 플레이스홀더 판정만 하며,
 * {@link #rejectPlaceholder(String, String)} 진입 시점의 {@code secret} 은 non-null 을 전제한다.
 */
public final class SecretPlaceholderGuard {

    /** {@code .env.example} 에 동봉된 플레이스홀더 (정확 일치, 소문자). */
    private static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "change-this-to-a-256-bit-secret-key-in-production",
            "change-this-mock-webhook-secret-in-production"
    );

    /** "교체하라"는 의도를 드러내는 마커 (부분문자열, 대소문자 무시). */
    private static final List<String> MARKERS = List.of("change-this", "change-me", "changeme");

    private SecretPlaceholderGuard() {
    }

    /**
     * 시크릿이 {@code .env.example} 플레이스홀더로 판단되면 {@link IllegalStateException} 을 던진다.
     *
     * @param propertyName 오류 메시지에 노출할 설정 키 (예: {@code "jwt.secret"})
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
