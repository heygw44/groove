package com.groove.auth.application;

import com.groove.auth.domain.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Refresh 토큰의 강제 무효화 작업을 REQUIRES_NEW 별도 트랜잭션으로 수행해 외부 롤백과 독립적으로 커밋한다.
 */
@Service
public class RefreshTokenAdmin {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenAdmin(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int forceRevokeAllActiveSessions(Long memberId, Instant now) {
        return refreshTokenRepository.revokeAllActiveByMemberId(memberId, now);
    }
}
