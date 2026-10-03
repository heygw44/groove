package com.groove.admin.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 현재 요청의 클라이언트 IP 조회. 요청 스코프 밖(스케줄러 등)에서 호출되면 null.
 *
 * <p>Nginx 는 {@code $proxy_add_x_forwarded_for} 로 요청자가 보낸 X-Forwarded-For 뒤에 자신이 본
 * {@code $remote_addr} 를 덧붙인다. 앞쪽 값은 요청자가 마음대로 쓸 수 있으므로 신뢰하는 프록시가
 * 덧붙인 마지막 값만 쓴다. Nginx 에서 헤더를 덮어쓰지 않고 앱에서 고르는 이유: 덮어쓰기는 모든 프록시
 * location 에 설정이 필요해 새 location 이 빠뜨리면 조용히 구멍이 다시 열리고, 이 규칙은 단위 테스트로 고정된다.
 *
 * <p>앞단에 프록시 홉(로드밸런서/CDN)이 추가되면 마지막 값이 그 프록시 주소가 되므로 이 규칙도 함께 바꿔야 한다.
 */
@Component
public class ClientIpResolver {

	private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
	private static final int MAX_LENGTH = 45;

	public String resolve() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
			return null;
		}
		HttpServletRequest request = servletAttributes.getRequest();
		String ip = lastForwardedFor(request.getHeader(FORWARDED_FOR_HEADER));
		if (ip == null) {
			ip = request.getRemoteAddr();
		}
		return truncate(ip);
	}

	private String lastForwardedFor(String forwardedFor) {
		if (forwardedFor == null) {
			return null;
		}
		String last = forwardedFor.substring(forwardedFor.lastIndexOf(',') + 1).trim();
		return last.isEmpty() ? null : last;
	}

	private String truncate(String ip) {
		if (ip == null || ip.length() <= MAX_LENGTH) {
			return ip;
		}
		return ip.substring(0, MAX_LENGTH);
	}
}
