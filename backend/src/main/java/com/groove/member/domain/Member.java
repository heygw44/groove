package com.groove.member.domain;

import com.groove.common.hash.Sha256Hasher;
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
import java.util.Locale;

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

    /**
     * 이메일 점유 해시 (#170, 패턴 A). 정규화된 이메일의 SHA-256 hex (64자) — {@link #hashEmail} 가 계산한다.
     *
     * <p>가입 중복 검사({@code MemberService.signup})와 재가입 차단의 권위 컬럼이다. 탈퇴 익명화는 {@code email}
     * 평문을 치환하지만 이 해시는 보존하므로, 탈퇴 후에도 같은 이메일의 재가입이 차단된다(어뷰징 방지).
     */
    @Column(name = "email_hash", nullable = false, length = 64, unique = true)
    private String emailHash;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 전화번호. 활성 회원은 가입 시 도메인 검증으로 항상 non-null 이지만, 탈퇴 익명화({@link #anonymize})가
     * NULL 로 비울 수 있어 컬럼은 nullable 이다 (#170).
     */
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

    private Member(String email, String passwordHash, String name, String phone, MemberRole role) {
        this.email = email;
        this.emailHash = hashEmail(email);
        this.password = passwordHash;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.emailVerified = false;
    }

    /**
     * 이메일 점유 해시 단일 진입점 (#170). 정규화(소문자화·trim) 후 SHA-256 hex 로 인코딩한다.
     *
     * <p>정규화하는 이유: 기존 {@code uk_member_email} 의 {@code utf8mb4_unicode_ci} collation 은 대소문자를
     * 구분하지 않아 {@code Foo@x.com} 과 {@code foo@x.com} 을 같은 이메일로 차단해 왔다. 해시 점유로
     * 전환하면서 정규화를 하지 않으면 두 값의 해시가 달라져 재가입 차단(패턴 A)이 약해지므로, 저장(가입)과
     * 검사(중복) 양쪽이 이 메서드를 공유해 동일 시맨틱을 유지한다. 마이그레이션 백필
     * ({@code SHA2(LOWER(TRIM(email)),256)}) 도 ASCII 이메일 기준 이 결과와 동치다.
     */
    public static String hashEmail(String email) {
        return Sha256Hasher.hex(email.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * 회원가입 정적 팩토리.
     *
     * @param passwordHash 반드시 BCrypt 등으로 해시된 값. 평문 금지.
     */
    public static Member register(String email, String passwordHash, String name, String phone) {
        return new Member(email, passwordHash, name, phone, MemberRole.USER);
    }

    /**
     * 관리자 계정 정적 팩토리 (role=ADMIN).
     *
     * <p>일반 가입({@link #register})은 항상 role=USER 로 고정하므로, 관리자 부트스트랩은 이 팩토리를
     * 통해서만 생성한다. 현재 유일한 소비자는 로컬 데모 시더({@code LocalDataSeeder}) 다 —
     * 운영 환경의 관리자 계정 발급 경로가 생기면 그쪽도 이 팩토리를 재사용한다.
     *
     * @param passwordHash 반드시 BCrypt 등으로 해시된 값. 평문 금지.
     */
    public static Member registerAdmin(String email, String passwordHash, String name, String phone) {
        return new Member(email, passwordHash, name, phone, MemberRole.ADMIN);
    }

    /**
     * 회원 탈퇴 (soft delete, #78, API.md §3.2 — DELETE /members/me).
     *
     * <p>{@code deleted_at} 에 탈퇴 시점을 기록할 뿐 행을 물리 삭제하지 않는다. 주문·결제 이력은
     * 보존되고({@code orders.member_id → member ON DELETE SET NULL} 은 hard delete 대비 안전망일 뿐
     * 본 흐름에서는 발동하지 않는다), 이메일은 {@link #emailHash} 로 점유 상태가 남아 재가입을 차단한다
     * (패턴 A — {@link MemberRepository#existsByEmailHash}). 평문 PII 제거는 {@link #anonymize} 가 맡는다 (#170).
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
     * 탈퇴 시 개인정보(PII) 익명화 (#170, GDPR/개인정보보호법 파기·익명화 의무).
     *
     * <p>{@code email} 평문은 {@code withdrawn-{id}@deleted.local} 로 치환해 제거하되, 재가입 차단의 권위
     * 컬럼인 {@link #emailHash} 는 보존한다 — 탈퇴 후에도 같은 이메일의 재가입을 막는다(패턴 A). {@code name} 은
     * 고정 라벨로, {@code phone} 은 NULL 로 비운다.
     *
     * <p>호출자({@code MemberService.withdraw})가 {@link #isWithdrawn()} 로 사전 분기해 활성 회원에 대해서만
     * {@code withdraw()} 직후 1회 호출하므로 별도 멱등 가드는 두지 않는다. {@code deleted_at} 이 익명화 여부의
     * 사실상 마커라 별도 anonymized 컬럼도 두지 않는다.
     */
    public void anonymize() {
        this.email = "withdrawn-" + this.id + "@deleted.local";
        this.name = "탈퇴회원";
        this.phone = null;
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
