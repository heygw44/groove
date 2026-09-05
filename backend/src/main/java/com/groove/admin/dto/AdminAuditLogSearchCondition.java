package com.groove.admin.dto;

import java.time.LocalDateTime;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;

/** {@link com.groove.admin.mapper.AdminAuditLogQueryMapper} 의 감사 로그 조회 조건. */
public record AdminAuditLogSearchCondition(
		AdminAuditAction action,
		AdminAuditTargetType targetType,
		Long adminId,
		LocalDateTime fromAt,
		LocalDateTime toExclusiveAt,
		int page,
		int size
) {

	public int offset() {
		return page * size;
	}
}
