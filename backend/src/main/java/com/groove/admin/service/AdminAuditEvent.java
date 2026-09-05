package com.groove.admin.service;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;

/** 관리자 감사 로그 기록 요청 이벤트. */
public record AdminAuditEvent(
		Long adminId,
		AdminAuditAction action,
		AdminAuditTargetType targetType,
		Long targetId,
		String detail,
		String ipAddress
) {
}
