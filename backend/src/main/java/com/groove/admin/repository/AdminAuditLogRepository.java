package com.groove.admin.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.admin.entity.AdminAuditLog;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

	List<AdminAuditLog> findAllByAdminIdOrderByIdAsc(Long adminId);
}
