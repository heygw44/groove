package com.groove.cart.application;

import com.groove.member.event.MemberWithdrawnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * MemberWithdrawnEvent 를 AFTER_COMMIT 으로 받아 해당 회원의 cart 를 삭제하는 리스너.
 * AFTER_COMMIT 시점에는 활성 트랜잭션이 없으므로 DB 쓰기는 REQUIRES_NEW 자체 트랜잭션으로 수행한다.
 * 정리 실패 시 예외는 ERROR 로그로 흡수한다.
 */
@Component
public class CartCleanupOnMemberWithdrawnListener {

    private static final Logger log = LoggerFactory.getLogger(CartCleanupOnMemberWithdrawnListener.class);

    private final CartService cartService;

    public CartCleanupOnMemberWithdrawnListener(CartService cartService) {
        this.cartService = cartService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawnEvent event) {
        try {
            cartService.deleteForMember(event.memberId());
            log.info("회원 탈퇴 장바구니 정리 완료 memberId={}", event.memberId());
        } catch (RuntimeException e) {
            log.error("회원 탈퇴 장바구니 정리 실패 memberId={} — 탈퇴는 확정, 빈 cart 잔존 가능(운영 보강 대상)",
                    event.memberId(), e);
        }
    }
}
