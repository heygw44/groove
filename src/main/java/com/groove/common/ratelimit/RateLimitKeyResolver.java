package com.groove.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

@FunctionalInterface
public interface RateLimitKeyResolver {

    String resolveKey(HttpServletRequest request);

    /**
     * 클라이언트 IP를 키로 사용한다. {@code request.getRemoteAddr()} 만 사용하므로,
     * 프록시 뒤에서 운영할 때는 반드시 {@code server.forward-headers-strategy=native} 또는
     * {@code framework} 를 설정해 컨테이너가 X-Forwarded-* 를 검증/변환하게 해야 한다.
     * 헤더를 직접 신뢰하면 IP 스푸핑으로 Rate Limit 을 우회할 수 있다.
     */
    static RateLimitKeyResolver clientIp() {
        return HttpServletRequest::getRemoteAddr;
    }
}
