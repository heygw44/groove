package com.groove.cart.application;

import com.groove.cart.domain.Cart;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 장바구니 쓰기 오케스트레이터. 트랜잭션 경계는 협력 빈 CartSteps 가 가지며, 이 빈은 비-트랜잭션이다.
 * 동일 회원의 동시 쓰기(더블클릭 등)로 uk_cart_member / uk_cart_item_cart_album 가 커밋 시점에 충돌하면,
 * addItem 은 1회 재조회/누적 재시도로 멱등하게 흡수한다(승자의 cart/cart_item 을 다시 읽어 누적 UPDATE 경로로 수렴).
 * 그래도 남는 제약 위반은 GlobalExceptionHandler 가 409(DATA_INTEGRITY_CONFLICT)로 매핑한다(500 아님).
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
            // uk_cart_member 또는 uk_cart_item_cart_album 경합에서 패배 — 승자가 이미 커밋했다.
            // 1회 재시도: 두 번째 시도의 getOrCreate 는 승자의 cart 를, addOrAccumulate 는 승자의 행을
            // 재조회해 누적(UPDATE) 분기를 타므로 UNIQUE 를 다시 위반하지 않는다.
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
