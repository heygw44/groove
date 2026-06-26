package com.groove.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 보안 응답 헤더 설정 — 현재는 Content-Security-Policy(CSP) 만 다룬다.
 *
 * <p>{@code reportOnly=true}(기본)면 위반을 차단하지 않고 보고만 하는
 * {@code Content-Security-Policy-Report-Only} 헤더로 발급한다. 운영에서 위반을 충분히
 * 관측한 뒤 {@code GROOVE_CSP_REPORT_ONLY=false} 로 enforce({@code Content-Security-Policy})
 * 전환한다. {@code enabled=false} 면 CSP 헤더를 발급하지 않는다(X-Frame-Options 등 Spring 기본 헤더는 유지).
 */
@ConfigurationProperties(prefix = "groove.security.csp")
public record SecurityHeadersProperties(
        Boolean enabled,
        Boolean reportOnly,
        String policy
) {

    /**
     * Toss 결제 위젯(iframe·리소스)을 허용하는 기본 CSP. 값 미설정 시 사용한다.
     *
     * <p>위반 리포트의 중앙 수집을 원하면(현재 앱에 수집 엔드포인트 없음) {@code GROOVE_CSP_POLICY} 로 정책을 재정의하며
     * {@code report-to <group>} 디렉티브를 덧붙이고, 별도로 {@code Reporting-Endpoints} 헤더에 수집 URL 을 연결한다.
     */
    private static final String DEFAULT_POLICY = "default-src 'self'; "
            + "script-src 'self' https://*.tosspayments.com; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: https:; "
            + "font-src 'self' data:; "
            + "connect-src 'self' https://*.tosspayments.com; "
            + "frame-src 'self' https://*.tosspayments.com; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    public SecurityHeadersProperties {
        enabled = enabled != null ? enabled : Boolean.TRUE;
        reportOnly = reportOnly != null ? reportOnly : Boolean.TRUE;
        policy = (policy != null && !policy.isBlank()) ? policy : DEFAULT_POLICY;
    }
}
