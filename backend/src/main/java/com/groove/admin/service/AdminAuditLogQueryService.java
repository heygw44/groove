package com.groove.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.dto.AdminAuditLogResponse;
import com.groove.admin.dto.AdminAuditLogSearchCondition;
import com.groove.admin.dto.AdminAuditLogSearchRequest;
import com.groove.admin.mapper.AdminAuditLogQueryMapper;
import com.groove.global.common.PageResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 감사 로그 목록 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminAuditLogQueryService {

	private final AdminAuditLogQueryMapper adminAuditLogQueryMapper;

	public PageResponse<AdminAuditLogResponse> getList(AdminAuditLogSearchRequest request) {
		AdminAuditLogSearchCondition condition = request.toCondition();
		long totalElements = adminAuditLogQueryMapper.countAuditLogs(condition);
		if (totalElements == 0) {
			return PageResponse.of(List.of(), condition.page(), condition.size(), 0);
		}
		List<AdminAuditLogResponse> content = adminAuditLogQueryMapper.findAuditLogs(condition);
		return PageResponse.of(content, condition.page(), condition.size(), totalElements);
	}
}
