package com.groove.admin.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;

import lombok.RequiredArgsConstructor;

/** 관리자 행위 감사 로그 기록. 실제 저장은 커밋 이후 이벤트로 위임한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminAuditLogService {

	private final ApplicationEventPublisher eventPublisher;
	private final ClientIpResolver clientIpResolver;

	public void record(Long adminId, AdminAuditAction action, AdminAuditTargetType targetType, Long targetId,
			String detail) {
		String ipAddress = clientIpResolver.resolve();
		eventPublisher.publishEvent(new AdminAuditEvent(adminId, action, targetType, targetId, detail, ipAddress));
	}
}
