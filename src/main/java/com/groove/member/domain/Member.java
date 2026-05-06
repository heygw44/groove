package com.groove.member.domain;

import com.groove.common.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 회원 엔티티 (ERD §4.1).
 *
 * <p>비밀번호는 항상 BCrypt 해시로 저장된다 ({@code MemberService.signup} 에서 강제).
 * 평문은 서비스 레이어를 넘어 엔티티에 도달하지 못한다.
 */
@Entity
@Table(name = "member")
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Member() {
    }

    private Member(String email, String passwordHash, String name, String phone) {
        this.email = email;
        this.password = passwordHash;
        this.name = name;
        this.phone = phone;
        this.role = MemberRole.USER;
        this.emailVerified = false;
    }

    /**
     * 회원가입 정적 팩토리.
     *
     * @param passwordHash 반드시 BCrypt 등으로 해시된 값. 평문 금지.
     */
    public static Member register(String email, String passwordHash, String name, String phone) {
        return new Member(email, passwordHash, name, phone);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public MemberRole getRole() {
        return role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
