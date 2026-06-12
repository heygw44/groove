package com.groove.auth.application;

import com.groove.member.event.MemberWithdrawnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

/**
 * 회원 탈퇴 시 리프레시 토큰을 정리하는 리스너 (#78) — MemberWithdrawnEvent 를 AFTER_COMMIT 으로
 * 받아 해당 회원의 활성 refresh 토큰을 일괄 revoke 한다.
 *
 * best-effort 인 이유: soft delete 가 회전을 이미 차단한다. RefreshTokenService.rotate 가
 * findByIdAndDeletedAtIsNull 로 활성 회원을 확인하므로, 탈퇴 회원의 refresh 토큰은 남아 있어도 새
 * access 토큰을 받아낼 수 없다. 따라서 이 revoke 는 토큰 행을 즉시 무효화해 흔적을 남기지 않으려는 위생
 * 처리이며, 정합성의 1차 방어선이 아니다.
 *
 * 트랜잭션: RefreshTokenAdmin.forceRevokeAllActiveSessions 가 이미 REQUIRES_NEW 라 호출만으로 별도
 * 트랜잭션이 열려 커밋된다 — 리스너 자체에는 @Transactional 이 불필요하다(AFTER_COMMIT 시점엔 활성
 * 트랜잭션이 없다). 실패해도 이미 커밋된 탈퇴에는 영향이 없으므로 예외는 로그로 흡수한다.
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
