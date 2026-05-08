package com.groove.cart.domain;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.cart.exception.CartItemNotFoundException;
import com.groove.cart.exception.CartQuantityLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cart 도메인 — 누적/수량 변경/제거/clear")
class CartTest {

    private Album album(long id) {
        Artist artist = Artist.create("A", null);
        Genre genre = Genre.create("G");
        Label label = Label.create("L");
        Album album = Album.create("Title-" + id, artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, 30000L, 100,
                AlbumStatus.SELLING, false, null, null);
        ReflectionTestUtils.setField(album, "id", id);
        return album;
    }

    @Test
    @DisplayName("addOrAccumulate — 처음 추가 시 새 항목 생성")
    void addOrAccumulate_first() {
        Cart cart = Cart.openFor(1L);

        cart.addOrAccumulate(album(10L), 2);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("addOrAccumulate — 동일 albumId 재추가 시 quantity 누적")
    void addOrAccumulate_accumulates() {
        Cart cart = Cart.openFor(1L);
        Album album = album(10L);

        cart.addOrAccumulate(album, 2);
        cart.addOrAccumulate(album, 3);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("addOrAccumulate — 누적 결과가 MAX_ITEM_QUANTITY 초과 → CartQuantityLimitExceededException")
    void addOrAccumulate_exceedsLimit() {
        Cart cart = Cart.openFor(1L);
        Album album = album(10L);
        cart.addOrAccumulate(album, Cart.MAX_ITEM_QUANTITY);

        assertThatThrownBy(() -> cart.addOrAccumulate(album, 1))
                .isInstanceOf(CartQuantityLimitExceededException.class);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(Cart.MAX_ITEM_QUANTITY);
    }

    @Test
    @DisplayName("changeItemQuantity — 존재하지 않는 itemId → CartItemNotFoundException")
    void changeItemQuantity_notFound() {
        Cart cart = Cart.openFor(1L);

        assertThatThrownBy(() -> cart.changeItemQuantity(999L, 3))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    @DisplayName("removeItem — 존재하지 않는 itemId → CartItemNotFoundException")
    void removeItem_notFound() {
        Cart cart = Cart.openFor(1L);

        assertThatThrownBy(() -> cart.removeItem(999L))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    @DisplayName("clear — 모든 항목 제거")
    void clear_removesAll() {
        Cart cart = Cart.openFor(1L);
        cart.addOrAccumulate(album(10L), 2);
        cart.addOrAccumulate(album(20L), 1);

        cart.clear();

        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("getItems — 외부에서 변경 불가 (불변 뷰)")
    void getItems_unmodifiable() {
        Cart cart = Cart.openFor(1L);

        assertThatThrownBy(() -> cart.getItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
