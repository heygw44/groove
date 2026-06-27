package com.groove.cart.application;

import com.groove.cart.domain.CartRepository;
import com.groove.catalog.album.application.AlbumReferenceGuard;
import org.springframework.stereotype.Component;

/** 장바구니 항목이 앨범을 참조하는지 확인하는 {@link AlbumReferenceGuard} 구현(catalog→cart 역참조 차단, #349). */
@Component
public class CartAlbumReferenceGuard implements AlbumReferenceGuard {

    private final CartRepository cartRepository;

    public CartAlbumReferenceGuard(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Override
    public boolean isReferenced(Long albumId) {
        return cartRepository.existsByAlbumId(albumId);
    }
}
