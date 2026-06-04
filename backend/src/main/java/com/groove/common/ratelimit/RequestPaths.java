package com.groove.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Rate Limit 정책 매칭용 경로 정규화 유틸.
 *
 * <p>{@link HttpServletRequest#getRequestURI()} 는 raw 값이라, Spring 핸들러 매핑이 정규화 후
 * 라우팅하는 경로와 차이가 생길 수 있다. 그 간극을 그대로 두면 {@code //}, {@code /./}, 매트릭스
 * 파라미터 같은 변형으로 정책 매칭을 우회한 채 컨트롤러까지 도달하는 시나리오가 발생할 수 있어,
 * 본 유틸이 동일한 정규화 결과를 보장한다.
 */
public final class RequestPaths {

    private RequestPaths() {
    }

    /**
     * 매트릭스 파라미터 제거, 연속 슬래시 단일화, {@code ./..} 정리를 적용한 경로를 반환한다.
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
