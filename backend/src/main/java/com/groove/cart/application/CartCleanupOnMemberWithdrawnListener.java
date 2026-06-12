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
 * 회원 탈퇴 시 장바구니를 정리하는 리스너 (#78) — MemberWithdrawnEvent 를 AFTER_COMMIT 으로 받아 해당 회원의 cart 를 삭제한다.
 *
 * AFTER_COMMIT + REQUIRES_NEW 인 이유: ShippingCreationListener(#W7-6)와 동일한 근거다. 이벤트는 탈퇴(soft delete)
 * 트랜잭션 안에서 발행되며, AFTER_COMMIT 바인딩이면 그 트랜잭션 커밋 뒤에만 실행되므로 "확정되지 않은 탈퇴"에 장바구니가
 * 지워지는 일이 없다. 단 AFTER_COMMIT 시점에는 활성 트랜잭션이 없으므로 DB 쓰기를 하려면 자체 트랜잭션(REQUIRES_NEW)이
 * 필요하다.
 *
 * 실패 격리: 장바구니 정리 실패가 이미 커밋된 탈퇴를 되돌려서는 안 되므로 예외는 여기서 로그로 흡수한다. 남은 빈 cart 는
 * 보안·정합성에 영향이 없는 best-effort 정리 대상(조용히 삼키지 않고 ERROR 로 남김).
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
