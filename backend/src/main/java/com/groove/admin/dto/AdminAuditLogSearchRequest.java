package com.groove.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record AdminAuditLogSearchRequest(
		AdminAuditAction action,
		AdminAuditTargetType targetType,
		Long adminId,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@PositiveOrZero Integer page,
		@Min(1) @Max(100) Integer size
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;

	@AssertTrue(message = "from 은 to 보다 이후일 수 없습니다.")
	public boolean isValidPeriod() {
		return from == null || to == null || !from.isAfter(to);
	}

	public AdminAuditLogSearchCondition toCondition() {
		int resolvedPage = page == null ? DEFAULT_PAGE : page;
		int resolvedSize = size == null ? DEFAULT_SIZE : size;
		LocalDateTime fromAt = from == null ? null : from.atStartOfDay();
		LocalDateTime toExclusiveAt = to == null ? null : to.plusDays(1).atStartOfDay();
		return new AdminAuditLogSearchCondition(action, targetType, adminId, fromAt, toExclusiveAt, resolvedPage,
				resolvedSize);
	}
}
