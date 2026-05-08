package com.groove.cart.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.cart.domain.Cart;
import com.groove.cart.domain.CartRepository;
import com.groove.cart.exception.AlbumNotPurchasableException;
import com.groove.cart.exception.CartItemNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService 단위 테스트")
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private AlbumRepository albumRepository;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, albumRepository);
    }

    private Album album(long id, AlbumStatus status) {
        Artist artist = Artist.create("A", null);
        Genre genre = Genre.create("G");
        Label label = Label.create("L");
        Album album = Album.create("Title-" + id, artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, 30000L, 100,
                status, false, null, null);
        ReflectionTestUtils.setField(album, "id", id);
        return album;
    }

    @Test
    @DisplayName("find → cart 없으면 비영속 빈 Cart 반환 (관찰만으로 영속화하지 않음)")
    void find_returnsEmptyCartWhenAbsent() {
        given(cartRepository.findByMemberIdWithItems(1L)).willReturn(Optional.empty());

        Cart cart = cartService.find(1L);

        assertThat(cart.getId()).isNull();
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("addItem → cart 가 없으면 자동 생성 후 항목 추가")
    void addItem_lazyCreatesCart() {
        given(cartRepository.findByMemberIdWithItems(1L)).willReturn(Optional.empty());
        given(cartRepository.save(any(Cart.class))).willAnswer(inv -> inv.getArgument(0));
        given(albumRepository.findById(10L)).willReturn(Optional.of(album(10L, AlbumStatus.SELLING)));

        Cart cart = cartService.addItem(1L, 10L, 2);

        assertThat(cart.getMemberId()).isEqualTo(1L);
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("addItem → 동일 album 재추가 시 quantity 누적")
    void addItem_accumulates() {
        Cart existing = Cart.openFor(1L);
        existing.addOrAccumulate(album(10L, AlbumStatus.SELLING), 2);
        given(cartRepository.findByMemberIdWithItems(1L)).willReturn(Optional.of(existing));
        given(albumRepository.findById(10L)).willReturn(Optional.of(album(10L, AlbumStatus.SELLING)));

        Cart cart = cartService.addItem(1L, 10L, 3);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("addItem → album 이 HIDDEN 이면 AlbumNotPurchasableException")
    void addItem_hiddenAlbumRejected() {
        given(albumRepository.findById(10L)).willReturn(Optional.of(album(10L, AlbumStatus.HIDDEN)));

        assertThatThrownBy(() -> cartService.addItem(1L, 10L, 2))
                .isInstanceOf(AlbumNotPurchasableException.class);
    }

    @Test
    @DisplayName("addItem → album 이 SOLD_OUT 이면 AlbumNotPurchasableException")
    void addItem_soldOutAlbumRejected() {
        given(albumRepository.findById(10L)).willReturn(Optional.of(album(10L, AlbumStatus.SOLD_OUT)));

        assertThatThrownBy(() -> cartService.addItem(1L, 10L, 2))
                .isInstanceOf(AlbumNotPurchasableException.class);
    }

    @Test
    @DisplayName("addItem → 존재하지 않는 albumId → AlbumNotFoundException")
    void addItem_albumNotFound() {
        given(albumRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(1L, 99L, 2))
                .isInstanceOf(AlbumNotFoundException.class);
    }

    @Test
    @DisplayName("removeItem → 본인 cart 에 없는 itemId → CartItemNotFoundException")
    void removeItem_notInOwnCart() {
        Cart empty = Cart.openFor(1L);
        given(cartRepository.findByMemberIdWithItems(1L)).willReturn(Optional.of(empty));

        assertThatThrownBy(() -> cartService.removeItem(1L, 12345L))
                .isInstanceOf(CartItemNotFoundException.class);
    }
}
