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
 * Refresh Token 영속 엔티티. 토큰 본문은 저장하지 않고 SHA-256 해시를 tokenHash(CHAR(64), UNIQUE)로 저장해 lookup 한다.
 * 회전 체인은 replacedByTokenId 로 추적한다 — 회전은 revoke 하며 새 행 id 를 채우고, 강제 무효화는 id 없이 revokedAt 만 설정.
 * revokedAt·replacedByTokenId 는 Repository 의 @Modifying JPQL 로만 변경하며 변경자 메서드를 노출하지 않는다.
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
     * 신규 발급 행 생성. tokenHash 는 호출자가 미리 SHA-256 해시해서 넘긴다.
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
