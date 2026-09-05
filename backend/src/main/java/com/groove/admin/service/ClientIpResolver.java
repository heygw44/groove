package com.groove.admin.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/** 현재 요청의 클라이언트 IP 조회. 요청 스코프 밖(스케줄러 등)에서 호출되면 null. */
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
		String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
		String ip = forwardedFor != null && !forwardedFor.isBlank()
				? forwardedFor.split(",")[0].trim()
				: request.getRemoteAddr();
		return truncate(ip);
	}

	private String truncate(String ip) {
		if (ip == null || ip.length() <= MAX_LENGTH) {
			return ip;
		}
		return ip.substring(0, MAX_LENGTH);
	}
}
