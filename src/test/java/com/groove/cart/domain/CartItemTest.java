package com.groove.cart.domain;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.cart.exception.CartQuantityLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CartItem — quantity 검증 + subtotal")
class CartItemTest {

    private Album album(long price) {
        Artist artist = Artist.create("A", null);
        Genre genre = Genre.create("G");
        Label label = Label.create("L");
        return Album.create("Title", artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, price, 100,
                AlbumStatus.SELLING, false, null, null);
    }

    @Test
    @DisplayName("create — quantity 1 미만 → CartQuantityLimitExceededException")
    void create_belowMin() {
        Cart cart = Cart.openFor(1L);

        assertThatThrownBy(() -> CartItem.create(cart, album(30000L), 0))
                .isInstanceOf(CartQuantityLimitExceededException.class);
    }

    @Test
    @DisplayName("create — quantity MAX 초과 → CartQuantityLimitExceededException")
    void create_aboveMax() {
        Cart cart = Cart.openFor(1L);

        assertThatThrownBy(() -> CartItem.create(cart, album(30000L), Cart.MAX_ITEM_QUANTITY + 1))
                .isInstanceOf(CartQuantityLimitExceededException.class);
    }

    @Test
    @DisplayName("changeQuantity — 1 미만 → CartQuantityLimitExceededException, 값 불변")
    void changeQuantity_belowMin() {
        Cart cart = Cart.openFor(1L);
        CartItem item = CartItem.create(cart, album(30000L), 3);

        assertThatThrownBy(() -> item.changeQuantity(0))
                .isInstanceOf(CartQuantityLimitExceededException.class);
        assertThat(item.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("subtotal = unitPrice * quantity")
    void subtotal() {
        Cart cart = Cart.openFor(1L);
        CartItem item = CartItem.create(cart, album(35000L), 2);

        assertThat(item.getSubtotal()).isEqualTo(70000L);
    }
}
