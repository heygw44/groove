package com.groove.admin.dto;

import java.time.LocalDateTime;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;

public record AdminAuditLogResponse(
		Long id,
		Long adminId,
		String adminNickname,
		AdminAuditAction action,
		AdminAuditTargetType targetType,
		Long targetId,
		String detail,
		String ipAddress,
		LocalDateTime createdAt
) {
}
