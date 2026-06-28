package com.groove.cart.application;

import com.groove.cart.domain.Cart;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 장바구니 쓰기 오케스트레이터. 트랜잭션 경계는 CartSteps 가 갖고 이 빈은 비-트랜잭션이다.
 * 동시 쓰기의 UNIQUE 충돌을 addItem 이 1회 재시도로 흡수하므로, 비-트랜잭션이어야 첫 실패가 롤백되고 재시도가 새 트랜잭션을 연다.
 */
@Service
public class CartService {

    private final CartSteps steps;

    public CartService(CartSteps steps) {
        this.steps = steps;
    }

    public Cart find(Long memberId) {
        return steps.find(memberId);
    }

    public Cart addItem(Long memberId, Long albumId, int quantity) {
        try {
            return steps.addItem(memberId, albumId, quantity);
        } catch (DataIntegrityViolationException race) {
            // UNIQUE 경합 패배 — 1회 재시도. 두 번째는 승자의 cart/행을 재조회해 누적(UPDATE) 분기를 타므로 다시 위반하지 않는다.
            return steps.addItem(memberId, albumId, quantity);
        }
    }

    public Cart changeItemQuantity(Long memberId, Long itemId, int quantity) {
        return steps.changeItemQuantity(memberId, itemId, quantity);
    }

    public Cart removeItem(Long memberId, Long itemId) {
        return steps.removeItem(memberId, itemId);
    }

    public Cart clear(Long memberId) {
        return steps.clear(memberId);
    }

    public void deleteForMember(Long memberId) {
        steps.deleteForMember(memberId);
    }
}
