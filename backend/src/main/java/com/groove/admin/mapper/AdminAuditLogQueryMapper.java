package com.groove.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.groove.admin.dto.AdminAuditLogResponse;
import com.groove.admin.dto.AdminAuditLogSearchCondition;

/** 관리자 감사 로그 조회 전용. */
@Mapper
public interface AdminAuditLogQueryMapper {

	List<AdminAuditLogResponse> findAuditLogs(AdminAuditLogSearchCondition condition);

	long countAuditLogs(AdminAuditLogSearchCondition condition);
}
