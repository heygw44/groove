package com.groove.member.entity;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원. 비밀번호는 이미 인코딩된 문자열만 받는다(인코딩은 auth 서비스 책임). */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "member", uniqueConstraints = @UniqueConstraint(name = "uk_member_email", columnNames = "email"))
public class Member extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String email;

	@Column(nullable = false, length = 100)
	private String password;

	@Column(nullable = false, length = 30)
	private String nickname;

	// ERD 는 VARCHAR(20). Hibernate 6 는 MySQL 에서 @Enumerated(STRING) 을 native ENUM 으로 만들어 강제한다.
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	@ColumnDefault("'USER'")
	private MemberRole role;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	@ColumnDefault("'ACTIVE'")
	private MemberStatus status;

	@Builder(access = PRIVATE)
	private Member(String email, String password, String nickname, MemberRole role, MemberStatus status) {
		this.email = email;
		this.password = password;
		this.nickname = nickname;
		this.role = role;
		this.status = status;
	}

	public static Member create(String email, String encodedPassword, String nickname) {
		return Member.builder()
				.email(email)
				.password(encodedPassword)
				.nickname(nickname)
				.role(MemberRole.USER)
				.status(MemberStatus.ACTIVE)
				.build();
	}

	public static Member createAdmin(String email, String encodedPassword, String nickname) {
		return Member.builder()
				.email(email)
				.password(encodedPassword)
				.nickname(nickname)
				.role(MemberRole.ADMIN)
				.status(MemberStatus.ACTIVE)
				.build();
	}

	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}

	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
	}

	public void withdraw() {
		if (isWithdrawn()) {
			throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN);
		}
		this.status = MemberStatus.WITHDRAWN;
	}

	public boolean isWithdrawn() {
		return this.status == MemberStatus.WITHDRAWN;
	}
}
