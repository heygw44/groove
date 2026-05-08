package com.groove.cart.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.cart.domain.Cart;
import com.groove.cart.domain.CartRepository;
import com.groove.cart.exception.AlbumNotPurchasableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 트랜잭션 경계.
 *
 * <p>회원당 cart 는 lazy 로 자동 생성된다 — {@link #getOrCreate(Long)} 가 모든 진입점의
 * 공통 시작점이다. 회원가입 흐름에서 eager 로 만들지 않는 이유는 휴면 회원에 빈 cart 행을
 * 생성하지 않기 위함.
 *
 * <p>동시성 정책: 본 이슈 범위(W6-1) 에서는 락 미적용. 동일 회원이 같은 album 을 동시에
 * POST /items 했을 때 발생하는 {@code uk_cart_item_cart_album} 충돌만 1회 재시도로 흡수한다
 * (실무 표준 패턴) — 그 외 경합(예: 동시 cart 생성 → {@code uk_cart_member} 충돌) 도 동일하게 흡수.
 *
 * <p>응답 시 LazyInitializationException 방지: 컨트롤러는 닫힌 세션에서 직렬화하므로,
 * 트랜잭션 내에서 {@code album} 연관(artist) 까지 강제 초기화한다 — 단건 cart 응답 한정.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final AlbumRepository albumRepository;

    public CartService(CartRepository cartRepository, AlbumRepository albumRepository) {
        this.cartRepository = cartRepository;
        this.albumRepository = albumRepository;
    }

    @Transactional(readOnly = true)
    public Cart find(Long memberId) {
        Cart cart = cartRepository.findByMemberIdWithItems(memberId)
                .orElseGet(() -> Cart.openFor(memberId));
        initializeAssociations(cart);
        return cart;
    }

    @Transactional
    public Cart addItem(Long memberId, Long albumId, int quantity) {
        Album album = loadPurchasableAlbum(albumId);
        try {
            Cart cart = getOrCreate(memberId);
            cart.addOrAccumulate(album, quantity);
            cartRepository.flush();
            initializeAssociations(cart);
            return cart;
        } catch (DataIntegrityViolationException ex) {
            // uk_cart_item_cart_album / uk_cart_member 동시성 충돌 — 한 번 재시도하면 누적 분기로 흡수된다.
            return retryAddItem(memberId, album, quantity, ex);
        }
    }

    @Transactional
    public Cart changeItemQuantity(Long memberId, Long itemId, int quantity) {
        Cart cart = getOrCreate(memberId);
        cart.changeItemQuantity(itemId, quantity);
        initializeAssociations(cart);
        return cart;
    }

    @Transactional
    public Cart removeItem(Long memberId, Long itemId) {
        Cart cart = getOrCreate(memberId);
        cart.removeItem(itemId);
        initializeAssociations(cart);
        return cart;
    }

    @Transactional
    public Cart clear(Long memberId) {
        Cart cart = getOrCreate(memberId);
        cart.clear();
        return cart;
    }

    /**
     * 회원당 1개 보장 (lazy). {@code uk_cart_member} 동시성 충돌 시 호출 측에서 재시도로 흡수.
     */
    private Cart getOrCreate(Long memberId) {
        return cartRepository.findByMemberIdWithItems(memberId)
                .orElseGet(() -> cartRepository.save(Cart.openFor(memberId)));
    }

    private Cart retryAddItem(Long memberId, Album album, int quantity, DataIntegrityViolationException original) {
        try {
            Cart cart = cartRepository.findByMemberIdWithItems(memberId)
                    .orElseGet(() -> cartRepository.save(Cart.openFor(memberId)));
            cart.addOrAccumulate(album, quantity);
            cartRepository.flush();
            initializeAssociations(cart);
            return cart;
        } catch (DataIntegrityViolationException retried) {
            // 두 번째 충돌은 정상 경합이 아니라 데이터 위반 — 원인을 보존해 던진다.
            throw retried;
        }
    }

    private Album loadPurchasableAlbum(Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(AlbumNotFoundException::new);
        if (album.getStatus() != AlbumStatus.SELLING) {
            throw new AlbumNotPurchasableException();
        }
        return album;
    }

    /**
     * cart_item 의 album 및 album.artist 프록시를 트랜잭션 내에서 강제 초기화한다.
     * GET /api/v1/cart 응답 DTO 에 artistName/albumTitle/coverImageUrl 이 포함되므로 필요.
     * label/genre 는 cart 응답에서 사용하지 않으므로 초기화하지 않는다.
     */
    private void initializeAssociations(Cart cart) {
        cart.getItems().forEach(item -> {
            Album album = item.getAlbum();
            album.getTitle();
            album.getArtist().getName();
        });
    }
}
