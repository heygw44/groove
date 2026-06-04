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
    }
}
