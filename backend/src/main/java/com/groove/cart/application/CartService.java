package com.groove.cart.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.cart.domain.Cart;
import com.groove.cart.domain.CartRepository;
import com.groove.cart.exception.AlbumNotPurchasableException;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 트랜잭션 경계. 회원당 cart 는 getOrCreate 에서 lazy 로 자동 생성된다.
 * 동일 회원의 동시 쓰기로 uk_cart_item_cart_album / uk_cart_member 가 충돌하면
 * DataIntegrityViolationException → 500 으로 노출된다. 응답 직렬화 전 album 연관(artist)을 강제 초기화한다.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final AlbumRepository albumRepository;
    private final MemberRepository memberRepository;

    public CartService(CartRepository cartRepository, AlbumRepository albumRepository,
                       MemberRepository memberRepository) {
        this.cartRepository = cartRepository;
        this.albumRepository = albumRepository;
        this.memberRepository = memberRepository;
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
        Cart cart = getOrCreate(memberId);
        cart.addOrAccumulate(album, quantity);
        initializeAssociations(cart);
        return cart;
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
     * 회원 탈퇴 시 장바구니 제거. 존재할 때만 삭제하며, 없으면 no-op 다(빈 cart 를 새로 만들지 않음).
     * cart_item 은 orphanRemoval=true + CascadeType.ALL 로 cart 삭제 시 함께 제거되므로 엔티티 삭제를 쓴다.
     */
    @Transactional
    public void deleteForMember(Long memberId) {
        cartRepository.findByMemberId(memberId).ifPresent(cartRepository::delete);
    }

    private Cart getOrCreate(Long memberId) {
        // 모든 장바구니 쓰기의 공통 진입점 — 탈퇴(soft delete)한 회원이면 여기서 404 로 차단한다.
        if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new MemberNotFoundException();
        }
        return cartRepository.findByMemberIdWithItems(memberId)
                .orElseGet(() -> cartRepository.save(Cart.openFor(memberId)));
    }

    private Album loadPurchasableAlbum(Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(AlbumNotFoundException::new);
        if (album.getStatus() != AlbumStatus.SELLING) {
            throw new AlbumNotPurchasableException();
        }
        return album;
    }

    /** cart_item 의 album 및 album.artist 프록시를 트랜잭션 내에서 강제 초기화한다. */
    private void initializeAssociations(Cart cart) {
        cart.getItems().forEach(item -> {
            Album album = item.getAlbum();
            album.getTitle();
            album.getArtist().getName();
        });
    }
}
