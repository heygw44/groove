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
 * <p>{@link #revokeIfActive} 는 동시 회전 race 방어용 conditional update 다.
 * 같은 토큰을 두 요청이 거의 동시에 보내면 첫 요청만 affected rows = 1 을 받고,
 * 두 번째는 0 을 받는다. 호출자(서비스)는 0 인 경우를 race 패배로 간주해 단순 거부한다.
 *
 * <p>UPDATE 쿼리는 {@code clearAutomatically=true, flushAutomatically=true} 로 설정해
 * 1차 캐시 stale 상태를 방지한다 — 같은 트랜잭션에서 UPDATE 후 동일 행을 다시 읽어도
 * 항상 DB 의 최신 상태를 반환한다.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 활성 상태일 때만 revoke 처리하는 atomic update.
     *
     * @return 업데이트된 행 수. 1 = 정상 회전, 0 = 이미 revoke 된 상태(재사용 또는 동시 회전 패자)
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
     * 특정 회원의 활성 refresh 토큰을 일괄 revoke. 재사용 감지 시 호출.
     *
     * @return revoke 된 행 수
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
