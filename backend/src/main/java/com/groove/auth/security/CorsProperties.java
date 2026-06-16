package com.groove.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 정책 설정. profile 별 yaml 에서 override 한다.
 *
 * compact constructor 에서 allow-credentials=true 와 host 전체 와일드카드(bare "*", http://*,
 * https://*, *://*, http://*:8080 등) 조합을 기동 시점에 거부한다. host 가 한정된 패턴(예:
 * https://*.example.com, http://localhost:[*])이나 명시적 origin 은 허용한다.
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
                && (allowedOrigins.contains("*")
                || allowedOriginPatterns.stream().anyMatch(CorsProperties::matchesAnyHost))) {
            throw new IllegalStateException(
                    "cors.allow-credentials=true 와 host 전체 와일드카드(\"*\", \"http://*\", \"https://*\" 등) 는 함께 쓸 수 없습니다 — 임의 사이트의 자격증명 포함 교차 출처 요청이 허용됩니다. host 가 한정된 패턴(예: http://localhost:[*], https://*.example.com)이나 명시적 origin 으로 제한하거나 allow-credentials 를 false 로 내리세요 (이슈 #166)");
        }
    }

    /**
     * origin 패턴의 host 부분이 정확히 bare "*" 인지 판정한다.
     * scheme(://) 이후, 포트(:)·경로(/) 구분자 이전까지를 host 로 본다.
     */
    private static boolean matchesAnyHost(String originPattern) {
        int schemeIdx = originPattern.indexOf("://");
        String afterScheme = schemeIdx >= 0 ? originPattern.substring(schemeIdx + 3) : originPattern;
        int hostEnd = afterScheme.length();
        for (int i = 0; i < afterScheme.length(); i++) {
            char c = afterScheme.charAt(i);
            if (c == ':' || c == '/') {
                hostEnd = i;
                break;
            }
        }
        return afterScheme.substring(0, hostEnd).equals("*");
    }
}
