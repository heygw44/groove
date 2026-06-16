package com.groove.auth.application;

import com.groove.member.event.MemberWithdrawnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

/**
 * 회원 탈퇴 시 리프레시 토큰을 정리하는 리스너 — MemberWithdrawnEvent 를 AFTER_COMMIT 으로
 * 받아 해당 회원의 활성 refresh 토큰을 일괄 revoke 한다. 예외는 로그로 흡수한다.
 */
@Component
public class RefreshTokenCleanupOnMemberWithdrawnListener {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupOnMemberWithdrawnListener.class);

    private final RefreshTokenAdmin refreshTokenAdmin;
    private final Clock clock;

    public RefreshTokenCleanupOnMemberWithdrawnListener(RefreshTokenAdmin refreshTokenAdmin, Clock clock) {
        this.refreshTokenAdmin = refreshTokenAdmin;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberWithdrawn(MemberWithdrawnEvent event) {
        try {
            int revoked = refreshTokenAdmin.forceRevokeAllActiveSessions(event.memberId(), clock.instant());
            log.info("회원 탈퇴 리프레시 토큰 정리 memberId={} revoked={}", event.memberId(), revoked);
        } catch (RuntimeException e) {
            log.error("회원 탈퇴 리프레시 토큰 정리 실패 memberId={} — 탈퇴는 확정, soft delete 가 rotate 를 차단",
                    event.memberId(), e);
        }
    }
}
