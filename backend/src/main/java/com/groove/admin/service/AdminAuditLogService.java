package com.groove.admin.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditLog;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.repository.AdminAuditLogRepository;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** 관리자 행위 감사 로그 기록. 호출자의 트랜잭션에 참여한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminAuditLogService {

	private final AdminAuditLogRepository adminAuditLogRepository;
	private final MemberRepository memberRepository;

	@Transactional
	public void record(Long adminId, AdminAuditAction action, AdminAuditTargetType targetType, Long targetId,
			String detail) {
		Member admin = memberRepository.getReferenceById(adminId);
		adminAuditLogRepository.save(AdminAuditLog.record(admin, action, targetType, targetId, detail));
	}
}
