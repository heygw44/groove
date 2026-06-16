package com.groove.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URISyntaxException;

/** Rate Limit 정책 매칭용 경로 정규화 유틸. */
public final class RequestPaths {

    private RequestPaths() {
    }

    /**
     * 매트릭스 파라미터 제거, 연속 슬래시 단일화, ./.. 정리를 적용한 경로를 반환한다.
     */
    public static String normalizedPath(HttpServletRequest request) {
        String raw = request.getRequestURI();
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        int semicolonIdx = raw.indexOf(';');
        String stripped = semicolonIdx >= 0 ? raw.substring(0, semicolonIdx) : raw;
        String collapsed = stripped.replaceAll("/{2,}", "/");
        try {
            String resolved = new URI(collapsed).normalize().getPath();
            return resolved == null ? collapsed : resolved;
        } catch (URISyntaxException e) {
            return collapsed;
        }
    }
}
