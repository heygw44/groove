package com.groove.cart.api.dto;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.cart.domain.CartItem;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 장바구니 항목 응답 (API §3.4).
 *
 * <p>{@code available} 은 GET 시점 재계산이다 — 추가 시점에 SELLING 이었더라도 운영 중
 * HIDDEN/SOLD_OUT 으로 전이될 수 있어, 클라이언트가 결제 진입 전 노출을 결정할 수 있도록
 * 매 응답마다 (status == SELLING && stock >= quantity) 로 재평가한다.
 */
public record CartItemResponse(
        @Schema(description = "장바구니 항목 ID", example = "1")
        Long itemId,

        @Schema(description = "앨범 ID", example = "1")
        Long albumId,

        @Schema(description = "앨범 제목", example = "Random Access Memories")
        String albumTitle,

        @Schema(description = "아티스트명", example = "Daft Punk")
        String artistName,

        @Schema(description = "커버 이미지 URL", example = "https://cdn.groove.com/covers/1.jpg")
        String coverImageUrl,

        @Schema(description = "단가 (단위 원)", example = "18500")
        long unitPrice,

        @Schema(description = "수량", example = "2")
        int quantity,

        @Schema(description = "소계 (단가 × 수량, 단위 원)", example = "37000")
        long subtotal,

        @Schema(description = "구매 가능 여부 (응답 시점 재계산 — 판매중 && 재고충분)", example = "true")
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
