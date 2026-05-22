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

    /**
     * 회원 탈퇴 (soft delete, #78, API.md §3.2 — DELETE /members/me).
     *
     * <p>{@code deleted_at} 에 탈퇴 시점을 기록할 뿐 행을 물리 삭제하지 않는다. 주문·결제 이력은
     * 보존되고({@code orders.member_id → member ON DELETE SET NULL} 은 hard delete 대비 안전망일 뿐
     * 본 흐름에서는 발동하지 않는다), 이메일은 점유 상태로 남아 재가입을 차단한다(패턴 A —
     * {@link MemberRepository#existsByEmail}).
     *
     * <p><b>멱등</b>: 이미 탈퇴한 회원에 다시 호출해도 최초 {@code deleted_at} 을 덮어쓰지 않는다.
     * 재탈퇴 요청을 no-op 으로 처리하기 위함이며, 호출자({@code MemberService.withdraw})는
     * {@link #isWithdrawn()} 으로 사전 분기해 이벤트 재발행도 막는다.
     */
    public void withdraw(Instant now) {
        if (this.deletedAt == null) {
            this.deletedAt = now;
        }
    }

    /**
     * 이미 탈퇴(soft delete)한 회원인지 여부.
     */
    public boolean isWithdrawn() {
        return this.deletedAt != null;
    }

    /**
     * 프로필 부분 수정 (#76, API.md §3.2 — PATCH /members/me).
     *
     * <p>부분 수정 규약: 인자가 {@code null} 인 필드는 변경하지 않는다 (미전송 = 미변경).
     * {@code email}·{@code role}·{@code password}·{@code emailVerified} 는 이 메서드로 변경할 수 없다.
     */
    public void updateProfile(String name, String phone) {
        if (name != null) {
            this.name = name;
        }
        if (phone != null) {
            this.phone = phone;
        }
    }

    /**
     * 비밀번호 교체 (#77, API.md §3.2 — PATCH /members/me/password).
     *
     * <p>현재 비밀번호 검증·신규 비밀번호 해싱은 호출자({@code AuthService.changePassword})가 책임지며,
     * 이 메서드는 이미 해시된 값을 받아 그대로 저장한다. {@code register} 와 동일하게 평문 금지.
     *
     * @param newPasswordHash 반드시 BCrypt 등으로 해시된 값. 평문 금지.
     */
    public void changePassword(String newPasswordHash) {
        this.password = newPasswordHash;
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
