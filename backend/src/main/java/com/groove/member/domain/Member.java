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

/** 회원 엔티티. 비밀번호는 항상 BCrypt 해시로 저장된다. */
@Entity
@Table(name = "member")
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    /**
     * 이메일 점유 해시. EmailHasher 가 계산한 정규화 이메일의 HMAC-SHA-256 값으로, 버전 prefix 를 포함한다
     * (예: v1:64hex). 가입 중복 검사·재가입 차단의 권위 컬럼이며, 탈퇴 익명화 후에도 보존된다.
     */
    @Column(name = "email_hash", nullable = false, length = 72, unique = true)
    private String emailHash;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 전화번호. 탈퇴 익명화가 NULL 로 비울 수 있어 컬럼은 nullable. */
    @Column(name = "phone", length = 20)
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

    private Member(String email, String emailHash, String passwordHash, String name, String phone, MemberRole role) {
        this.email = email;
        this.emailHash = emailHash;
        this.password = passwordHash;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.emailVerified = false;
    }

    /** 회원가입 정적 팩토리. emailHash 는 호출자가 EmailHasher 로 계산해 넘기고, passwordHash 는 BCrypt 해시값. */
    public static Member register(String email, String emailHash, String passwordHash, String name, String phone) {
        return new Member(email, emailHash, passwordHash, name, phone, MemberRole.USER);
    }

    /** 관리자 계정 정적 팩토리 (role=ADMIN). passwordHash 는 BCrypt 해시값. */
    public static Member registerAdmin(String email, String emailHash, String passwordHash, String name, String phone) {
        return new Member(email, emailHash, passwordHash, name, phone, MemberRole.ADMIN);
    }

    /**
     * 회원 탈퇴 (soft delete). deleted_at 에 탈퇴 시점을 기록할 뿐 물리 삭제하지 않는다. 이미 탈퇴한 회원에
     * 다시 호출해도 최초 deleted_at 을 덮어쓰지 않는다(멱등).
     */
    public void withdraw(Instant now) {
        if (this.deletedAt == null) {
            this.deletedAt = now;
        }
    }

    /** 이미 탈퇴(soft delete)한 회원인지 여부. */
    public boolean isWithdrawn() {
        return this.deletedAt != null;
    }

    /**
     * 탈퇴 시 개인정보(PII) 익명화. email 평문은 withdrawn-{id}@deleted.local 로 치환하되 emailHash 는 보존하고,
     * name 은 고정 라벨로, phone 은 NULL 로 비운다.
     */
    public void anonymize() {
        this.email = "withdrawn-" + this.id + "@deleted.local";
        this.name = "탈퇴회원";
        this.phone = null;
    }

    /**
     * 프로필 부분 수정 (name·phone). 인자가 null 인 필드는 변경하지 않는다.
     */
    public void updateProfile(String name, String phone) {
        if (name != null) {
            this.name = name;
        }
        if (phone != null) {
            this.phone = phone;
        }
    }

    /** 비밀번호 교체. 이미 해시된 BCrypt 값을 받아 그대로 저장한다. */
    public void changePassword(String newPasswordHash) {
        this.password = newPasswordHash;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailHash() {
        return emailHash;
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
