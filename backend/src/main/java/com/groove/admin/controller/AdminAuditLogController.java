package com.groove.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.admin.dto.AdminAuditLogResponse;
import com.groove.admin.dto.AdminAuditLogSearchRequest;
import com.groove.admin.service.AdminAuditLogQueryService;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Audit Log", description = "관리자 감사 로그 조회")
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

	private final AdminAuditLogQueryService adminAuditLogQueryService;

	@Operation(summary = "감사 로그 목록 조회")
	@GetMapping
	public ApiResponse<PageResponse<AdminAuditLogResponse>> getList(
			@Valid @ModelAttribute AdminAuditLogSearchRequest request) {
		return ApiResponse.ok(adminAuditLogQueryService.getList(request));
	}
}
