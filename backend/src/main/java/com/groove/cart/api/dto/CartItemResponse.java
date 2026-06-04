package com.groove.cart.api.dto;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.cart.domain.CartItem;

/**
 * 장바구니 항목 응답 (API §3.4).
 *
 * <p>{@code available} 은 GET 시점 재계산이다 — 추가 시점에 SELLING 이었더라도 운영 중
 * HIDDEN/SOLD_OUT 으로 전이될 수 있어, 클라이언트가 결제 진입 전 노출을 결정할 수 있도록
 * 매 응답마다 (status == SELLING && stock >= quantity) 로 재평가한다.
 */
public record CartItemResponse(
        Long itemId,
        Long albumId,
        String albumTitle,
        String artistName,
        String coverImageUrl,
        long unitPrice,
        int quantity,
        long subtotal,
        boolean available
) {

    public static CartItemResponse from(CartItem item) {
        Album album = item.getAlbum();
        boolean available = album.getStatus() == AlbumStatus.SELLING
                && album.getStock() >= item.getQuantity();
        return new CartItemResponse(
                item.getId(),
                album.getId(),
                album.getTitle(),
                album.getArtist().getName(),
                album.getCoverImageUrl(),
                album.getPrice(),
                item.getQuantity(),
                item.getSubtotal(),
                available
        );
    }
}
