package com.groove.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 정책 설정. profile 별 yaml 에서 override 한다.
 *
 * <p>운영 환경에서는 {@code allowed-origins} 를 명시적 화이트리스트로 제한하고,
 * 개발 환경에서만 {@code allowed-origin-patterns} 에 와일드카드를 허용한다.
 *
 * <p>{@code allow-credentials=true} 와 {@code allowed-origins("*")} 는 Spring 5.4+
 * 부터 충돌하므로 와일드카드는 반드시 {@code allowed-origin-patterns} 로 지정해야 한다.
 *
 * <p>다만 Spring 의 내장 검증({@code CorsConfiguration.validateAllowCredentials()})은
 * {@code allowed-origins} 의 bare {@code "*"} 만 거부하고 {@code allowed-origin-patterns}
 * 는 방치한다 — {@code allowed-origin-patterns: ["*"]} + {@code allow-credentials=true} 는
 * 요청 Origin 을 그대로 반사(reflect)하며 통과하므로, 임의 사이트의 자격증명 동반 교차 출처
 * 요청이 허용된다. 따라서 compact constructor 에서 두 목록의 와일드카드와 credentials 조합을
 * 기동 시점에 거부한다({@code JwtProperties}/{@code SecretPlaceholderGuard} 와 동일한
 * fail-fast). 구체적 포트 와일드카드(예: {@code http://localhost:[*]})는 허용된다(이슈 #166).
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        List<String> allowedOriginPatterns,
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        Boolean allowCredentials,
        Long maxAgeSeconds
) {

    private static final List<String> DEFAULT_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> DEFAULT_HEADERS = List.of("*");
    private static final long DEFAULT_MAX_AGE_SECONDS = 3600L;

    public CorsProperties {
        allowedOriginPatterns = allowedOriginPatterns != null ? allowedOriginPatterns : List.of();
        allowedOrigins = allowedOrigins != null ? allowedOrigins : List.of();
        allowedMethods = (allowedMethods != null && !allowedMethods.isEmpty()) ? allowedMethods : DEFAULT_METHODS;
        allowedHeaders = (allowedHeaders != null && !allowedHeaders.isEmpty()) ? allowedHeaders : DEFAULT_HEADERS;
        exposedHeaders = exposedHeaders != null ? exposedHeaders : List.of();
        allowCredentials = allowCredentials != null ? allowCredentials : Boolean.FALSE;
        maxAgeSeconds = maxAgeSeconds != null ? maxAgeSeconds : DEFAULT_MAX_AGE_SECONDS;

        if (Boolean.TRUE.equals(allowCredentials)
                && (allowedOrigins.contains("*") || allowedOriginPatterns.contains("*"))) {
            throw new IllegalStateException(
                    "cors.allow-credentials=true 와 allowed-origins/allowed-origin-patterns 의 와일드카드(\"*\") 는 함께 쓸 수 없습니다 — 임의 사이트의 자격증명 포함 교차 출처 요청이 허용됩니다. 와일드카드 대신 명시적 origin 또는 구체적 패턴(예: http://localhost:[*])으로 제한하거나 allow-credentials 를 false 로 내리세요 (이슈 #166)");
        }
    }
}
