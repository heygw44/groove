package com.groove.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Refresh 토큰 영속성.
 *
 * <p>revokeIfActive 는 conditional update 로, 같은 토큰을 동시에 보낸 두 요청 중
 * 첫 요청만 affected rows = 1, 두 번째는 0 을 받는다.
 *
 * <p>UPDATE 쿼리는 clearAutomatically=true, flushAutomatically=true 로 1차 캐시 stale 상태를 방지한다.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 활성 상태일 때만 revoke 처리하는 atomic update. 업데이트된 행 수를 반환한다(1=정상, 0=이미 revoke).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken r
               SET r.revokedAt = :now,
                   r.replacedByTokenId = :replacedBy
             WHERE r.id = :id
               AND r.revokedAt IS NULL
            """)
    int revokeIfActive(
            @Param("id") Long id,
            @Param("now") Instant now,
            @Param("replacedBy") Long replacedBy
    );

    /**
     * 특정 회원의 활성 refresh 토큰을 일괄 revoke 하고 revoke 된 행 수를 반환한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken r
               SET r.revokedAt = :now
             WHERE r.memberId = :memberId
               AND r.revokedAt IS NULL
            """)
    int revokeAllActiveByMemberId(
            @Param("memberId") Long memberId,
            @Param("now") Instant now
    );
}
