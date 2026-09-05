package com.groove.admin.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;
import com.groove.member.entity.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자 행위 감사 로그. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "admin_audit_log",
		indexes = @Index(name = "idx_audit_log_admin_created", columnList = "admin_id, created_at"))
public class AdminAuditLog extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "admin_id", nullable = false, foreignKey = @ForeignKey(name = "fk_audit_log_admin"))
	private Member admin;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 50)
	private AdminAuditAction action;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "target_type", nullable = false, length = 30)
	private AdminAuditTargetType targetType;

	@Column(name = "target_id", nullable = false)
	private Long targetId;

	@Column(columnDefinition = "TEXT")
	private String detail;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Builder(access = PRIVATE)
	private AdminAuditLog(Member admin, AdminAuditAction action, AdminAuditTargetType targetType, Long targetId,
			String detail, String ipAddress) {
		this.admin = admin;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.detail = detail;
		this.ipAddress = ipAddress;
	}

	public static AdminAuditLog record(Member admin, AdminAuditAction action, AdminAuditTargetType targetType,
			Long targetId, String detail, String ipAddress) {
		return AdminAuditLog.builder()
				.admin(admin)
				.action(action)
				.targetType(targetType)
				.targetId(targetId)
				.detail(detail)
				.ipAddress(ipAddress)
				.build();
	}
}
