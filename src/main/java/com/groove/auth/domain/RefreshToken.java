package com.groove.auth.domain;

import com.groove.common.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Refresh Token 영속 엔티티 (이슈 #22).
 *
 * <p>토큰 본문은 절대 저장하지 않는다. 클라이언트가 보낸 토큰을 SHA-256 해시한 값을
 * {@link #tokenHash} (CHAR(64)) 로 저장하고, 그 컬럼에 UNIQUE 제약을 걸어 lookup 한다.
 *
 * <p>회전 체인은 {@link #replacedByTokenId} 로 추적한다 — 회전 시 기존 행을 revoke 하면서
 * 새로 발급된 행 id 를 채워 넣는다. 로그아웃이나 재사용 감지로 인한 강제 무효화는
 * {@code replacedByTokenId} 가 null 인 채로 {@link #revokedAt} 만 설정된다.
 *
 * <p>{@code revokedAt}/{@code replacedByTokenId} 는 엔티티 메서드가 아니라 Repository 의
 * {@code @Modifying} JPQL 로만 변경된다 (CAS 경쟁 방어 + 일괄 무효화 효율). 따라서
 * 본 엔티티는 의도적으로 변경자 메서드를 노출하지 않는다.
 */
@Entity
@Table(
        name = "refresh_token",
        indexes = {
                @Index(name = "idx_refresh_member_revoked", columnList = "member_id, revoked_at")
        }
)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;

    protected RefreshToken() {
    }

    private RefreshToken(Long memberId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 신규 발급 행 생성. 평문 토큰은 호출자가 미리 SHA-256 해시해서 넘긴다.
     */
    public static RefreshToken issue(Long memberId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        return new RefreshToken(memberId, tokenHash, issuedAt, expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Long getReplacedByTokenId() {
        return replacedByTokenId;
    }
}
