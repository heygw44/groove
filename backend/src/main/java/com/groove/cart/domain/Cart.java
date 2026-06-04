package com.groove.cart.domain;

import com.groove.catalog.album.domain.Album;
import com.groove.cart.exception.CartItemNotFoundException;
import com.groove.cart.exception.CartQuantityLimitExceededException;
import com.groove.common.persistence.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 회원 장바구니 (ERD §4.7).
 *
 * <p>회원당 1개 ({@code uk_cart_member}). {@code member_id} 는 단순 long 컬럼으로 들고
 * 연관 엔티티로 매핑하지 않는다 — cart 는 member 의 라이프사이클에 종속되지만 (ON DELETE CASCADE)
 * 도메인 로직에서 member 를 직접 참조할 일이 없어 ID 만 저장하는 게 자족적이다.
 *
 * <p>cart_item 은 부모-자식 응집 (aggregate). {@code orphanRemoval=true} + {@code CascadeType.ALL}
 * 로 cart 를 통해서만 변경되며 별도 CartItemRepository 를 두지 않는다.
 */
@Entity
@Table(name = "cart")
public class Cart extends BaseTimeEntity {

    /**
     * 동일 상품 단일 행 quantity 상한. 재고 한도와 무관한 UI/오남용 방지선이다 — 재고 검증은
     * 주문 생성(W6-3) 시점에 수행한다 (cart 는 재고 가변성을 의도적으로 무시).
     */
    public static final int MAX_ITEM_QUANTITY = 99;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    private Cart(Long memberId) {
        this.memberId = memberId;
    }

    public static Cart openFor(Long memberId) {
        return new Cart(memberId);
    }

    /**
     * 동일 albumId 행이 있으면 quantity 를 누적하고, 없으면 새 CartItem 을 추가한다.
     * 누적 결과가 {@link #MAX_ITEM_QUANTITY} 를 넘으면 {@link CartQuantityLimitExceededException}.
     *
     * <p>호출 측({@code CartService}) 이 album.status == SELLING 검증을 끝낸 상태로 전달한다고 가정한다.
     */
    public CartItem addOrAccumulate(Album album, int quantity) {
        return findItemByAlbumId(album.getId())
                .map(existing -> {
                    existing.accumulate(quantity);
                    return existing;
                })
                .orElseGet(() -> {
                    CartItem item = CartItem.create(this, album, quantity);
                    items.add(item);
                    return item;
                });
    }

    public void changeItemQuantity(Long itemId, int quantity) {
        CartItem item = items.stream()
                .filter(i -> matchesItemId(i, itemId))
                .findFirst()
                .orElseThrow(CartItemNotFoundException::new);
        item.changeQuantity(quantity);
    }

    public void removeItem(Long itemId) {
        boolean removed = items.removeIf(i -> matchesItemId(i, itemId));
        if (!removed) {
            throw new CartItemNotFoundException();
        }
    }

    public void clear() {
        items.clear();
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private java.util.Optional<CartItem> findItemByAlbumId(Long albumId) {
        return items.stream()
                .filter(i -> i.getAlbum().getId().equals(albumId))
                .findFirst();
    }

    /**
     * 영속화 전 항목은 id 가 null 이라 비교 결과가 항상 false 가 되도록 가드한다 —
     * 호출 컨텍스트는 항상 영속화 후라 정상 흐름에서는 발생하지 않는다.
     */
    private static boolean matchesItemId(CartItem item, Long itemId) {
        return item.getId() != null && item.getId().equals(itemId);
    }
}
