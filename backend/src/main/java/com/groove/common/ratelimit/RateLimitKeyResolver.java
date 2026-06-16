package com.groove.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

@FunctionalInterface
public interface RateLimitKeyResolver {

    String resolveKey(HttpServletRequest request);

    /** 클라이언트 IP(request.getRemoteAddr())를 키로 사용한다. */
    static RateLimitKeyResolver clientIp() {
        return HttpServletRequest::getRemoteAddr;
    }
}
