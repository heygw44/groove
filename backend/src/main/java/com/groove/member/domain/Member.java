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
 * 비밀번호는 항상 BCrypt 해시로 저장된다 (MemberService.signup 에서 강제). 평문은 서비스 레이어를
 * 넘어 엔티티에 도달하지 못한다.
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
     * 이메일 점유 해시 (#170 패턴 A, #186). EmailHasher 가 계산한 정규화 이메일의 HMAC-SHA-256 값으로,
     * 키 롤링용 버전 prefix 를 포함한다(예: v1:<64 hex>). 가입 중복 검사(MemberService.signup)와 재가입
     * 차단의 권위 컬럼이다. 탈퇴 익명화는 email 평문을 치환하지만 이 해시는 보존하므로, 탈퇴 후에도 같은
     * 이메일의 재가입이 차단된다(어뷰징 방지).
     *
     * 해시는 엔티티가 아니라 서비스 레이어(EmailHasher 빈 — 서버 비밀키 주입 필요)에서 계산해 생성자로
     * 주입한다. 결정적 SHA-256 은 DB 유출 시 사전 대입으로 탈퇴 회원의 원본 이메일까지 역추적될 수 있어
     * HMAC 으로 전환했다(#186). 컬럼 폭은 prefix 수용을 위해 VARCHAR(72) 이다(V19 마이그레이션).
     */
    @Column(name = "email_hash", nullable = false, length = 72, unique = true)
    private String emailHash;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 전화번호. 활성 회원은 가입 시 도메인 검증으로 항상 non-null 이지만, 탈퇴 익명화(anonymize)가 NULL
     * 로 비울 수 있어 컬럼은 nullable 이다 (#170).
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

    private Member(String email, String emailHash, String passwordHash, String name, String phone, MemberRole role) {
        this.email = email;
        this.emailHash = emailHash;
        this.password = passwordHash;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.emailVerified = false;
    }

    /**
     * 회원가입 정적 팩토리.
     *
     * 이메일 점유 해시는 엔티티가 아니라 호출자(MemberService.signup)가 EmailHasher 로 미리 계산해 넘긴다
     * — 해시가 서버 비밀키(HMAC) 기반이라 엔티티에서 직접 계산할 수 없기 때문이다(#186).
     *
     * @param emailHash    EmailHasher.hash 로 계산한 점유 해시 (버전 prefix 포함, 예 v1:...)
     * @param passwordHash 반드시 BCrypt 등으로 해시된 값. 평문 금지.
     */
    public static Member register(String email, String emailHash, String passwordHash, String name, String phone) {
        return new Member(email, emailHash, passwordHash, name, phone, MemberRole.USER);
    }

    /**
     * 관리자 계정 정적 팩토리 (role=ADMIN).
     *
     * 일반 가입(register)은 항상 role=USER 로 고정하므로, 관리자 부트스트랩은 이 팩토리를 통해서만
     * 생성한다. 현재 유일한 소비자는 로컬 데모 시더(LocalDataSeeder) 다 — 운영 환경의 관리자 계정 발급
     * 경로가 생기면 그쪽도 이 팩토리를 재사용한다.
     *
     * @param emailHash    EmailHasher.hash 로 계산한 점유 해시 (버전 prefix 포함, #186)
     * @param passwordHash 반드시 BCrypt 등으로 해시된 값. 평문 금지.
     */
    public static Member registerAdmin(String email, String emailHash, String passwordHash, String name, String phone) {
        return new Member(email, emailHash, passwordHash, name, phone, MemberRole.ADMIN);
    }

    /**
     * 회원 탈퇴 (soft delete, #78, API.md §3.2 — DELETE /members/me).
     *
     * deleted_at 에 탈퇴 시점을 기록할 뿐 행을 물리 삭제하지 않는다. 주문·결제 이력은
     * 보존되고(orders.member_id → member ON DELETE SET NULL 은 hard delete 대비 안전망일 뿐 본 흐름에서는
     * 발동하지 않는다), 이메일은 emailHash 로 점유 상태가 남아 재가입을 차단한다(패턴 A —
     * MemberRepository.existsByEmailHash). 평문 PII 제거는 anonymize 가 맡는다 (#170).
     *
     * 멱등: 이미 탈퇴한 회원에 다시 호출해도 최초 deleted_at 을 덮어쓰지 않는다. 재탈퇴 요청을 no-op 으로
     * 처리하기 위함이며, 호출자(MemberService.withdraw)는 isWithdrawn() 으로 사전 분기해 이벤트 재발행도
     * 막는다.
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
     * email 평문은 withdrawn-{id}@deleted.local 로 치환해 제거하되, 재가입 차단의 권위 컬럼인 emailHash
     * 는 보존한다 — 탈퇴 후에도 같은 이메일의 재가입을 막는다(패턴 A). name 은 고정 라벨로, phone 은 NULL
     * 로 비운다.
     *
     * 호출자(MemberService.withdraw)가 isWithdrawn() 로 사전 분기해 활성 회원에 대해서만 withdraw() 직후
     * 1회 호출하므로 별도 멱등 가드는 두지 않는다. deleted_at 이 익명화 여부의 사실상 마커라 별도 anonymized
     * 컬럼도 두지 않는다.
     */
    public void anonymize() {
        this.email = "withdrawn-" + this.id + "@deleted.local";
        this.name = "탈퇴회원";
        this.phone = null;
    }

    /**
     * 프로필 부분 수정 (#76, API.md §3.2 — PATCH /members/me).
     *
     * 부분 수정 규약: 인자가 null 인 필드는 변경하지 않는다 (미전송 = 미변경).
     * email·role·password·emailVerified 는 이 메서드로 변경할 수 없다.
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
     * 현재 비밀번호 검증·신규 비밀번호 해싱은 호출자(AuthService.changePassword)가 책임지며, 이 메서드는
     * 이미 해시된 값을 받아 그대로 저장한다. register 와 동일하게 평문 금지.
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
