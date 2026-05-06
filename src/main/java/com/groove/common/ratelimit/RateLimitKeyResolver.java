package com.groove.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

@FunctionalInterface
public interface RateLimitKeyResolver {

    String resolveKey(HttpServletRequest request);

    static RateLimitKeyResolver clientIp() {
        return request -> {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
            return request.getRemoteAddr();
        };
    }
}
