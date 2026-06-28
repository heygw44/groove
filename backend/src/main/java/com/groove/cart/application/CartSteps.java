package com.groove.cart.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.cart.domain.Cart;
import com.groove.cart.domain.CartRepository;
import com.groove.cart.exception.AlbumNotPurchasableException;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 트랜잭션 단계 빈 — 각 메서드가 독립 트랜잭션 경계.
 * 동시 쓰기의 UNIQUE 충돌(커밋 시점 DataIntegrityViolationException)을 재시도로 흡수하는 책임은 CartService 에 있다.
 */
@Component
class CartSteps {

    private final CartRepository cartRepository;
    private final AlbumRepository albumRepository;
    private final MemberRepository memberRepository;

    CartSteps(CartRepository cartRepository, AlbumRepository albumRepository,
              MemberRepository memberRepository) {
        this.cartRepository = cartRepository;
        this.albumRepository = albumRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    Cart find(Long memberId) {
        Cart cart = cartRepository.findByMemberIdWithItems(memberId)
                .orElseGet(() -> Cart.openFor(memberId));
        initializeAssociations(cart);
        return cart;
    }

    @Transactional
    Cart addItem(Long memberId, Long albumId, int quantity) {
        Album album = loadPurchasableAlbum(albumId);
        Cart cart = getOrCreate(memberId);
        cart.addOrAccumulate(album, quantity);
        initializeAssociations(cart);
        return cart;
    }

    @Transactional
    Cart changeItemQuantity(Long memberId, Long itemId, int quantity) {
        Cart cart = getOrCreate(memberId);
        cart.changeItemQuantity(itemId, quantity);
        initializeAssociations(cart);
        return cart;
    }

    @Transactional
    Cart removeItem(Long memberId, Long itemId) {
        Cart cart = getOrCreate(memberId);
        cart.removeItem(itemId);
        initializeAssociations(cart);
        return cart;
    }

    @Transactional
    Cart clear(Long memberId) {
        Cart cart = getOrCreate(memberId);
        cart.clear();
        return cart;
    }

    /** 존재할 때만 삭제(빈 cart 새로 만들지 않음). cart_item 은 cascade+orphanRemoval 로 함께 제거. */
    @Transactional
    void deleteForMember(Long memberId) {
        cartRepository.findByMemberId(memberId).ifPresent(cartRepository::delete);
    }

    private Cart getOrCreate(Long memberId) {
        // 모든 장바구니 쓰기의 공통 진입점 — 탈퇴 회원을 여기서 404 로 차단.
        if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new MemberNotFoundException();
        }
        return cartRepository.findByMemberIdWithItems(memberId)
                .orElseGet(() -> cartRepository.save(Cart.openFor(memberId)));
    }

    private Album loadPurchasableAlbum(Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(AlbumNotFoundException::new);
        if (!album.isSelling()) {
            throw new AlbumNotPurchasableException();
        }
        return album;
    }

    /** 응답 직렬화 전 album·artist 프록시를 트랜잭션 내 초기화(Album @BatchSize 로 IN 일괄). */
    private void initializeAssociations(Cart cart) {
        cart.getItems().forEach(item -> {
            Album album = item.getAlbum();
            album.getTitle();
            album.getArtist().getName();
        });
    }
}
