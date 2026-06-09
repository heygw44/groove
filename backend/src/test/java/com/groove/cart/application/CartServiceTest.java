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
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService 단위 테스트")
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private AlbumRepository albumRepository;
    @Mock
    private MemberRepository memberRepository;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, albumRepository, memberRepository);
        // 활성 회원 기본값 — 가드(#187)는 쓰기 진입점 getOrCreate 에서 호출된다. 탈퇴 시나리오만 false 로 override 한다.
        lenient().when(memberRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);
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

    @Test
    @DisplayName("deleteForMember → cart 존재 시 삭제 위임 (#78 탈퇴 정리)")
    void deleteForMember_present_delegatesDelete() {
        Cart cart = Cart.openFor(1L);
        given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));

        cartService.deleteForMember(1L);

        verify(cartRepository).delete(cart);
    }

    @Test
    @DisplayName("deleteForMember → cart 없으면 no-op (삭제·생성 모두 하지 않음 — 빈 cart 안 만듦)")
    void deleteForMember_absent_noOpAndDoesNotCreate() {
        given(cartRepository.findByMemberId(1L)).willReturn(Optional.empty());

        cartService.deleteForMember(1L);

        verify(cartRepository, never()).delete(any(Cart.class));
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    @DisplayName("쓰기(clear) → 탈퇴(soft delete) 회원이면 MemberNotFoundException, cart 미조회·미저장 (#187)")
    void write_memberWithdrawn_throws() {
        long withdrawnMemberId = 99L;
        when(memberRepository.existsByIdAndDeletedAtIsNull(withdrawnMemberId)).thenReturn(false);

        assertThatThrownBy(() -> cartService.clear(withdrawnMemberId))
                .isInstanceOf(MemberNotFoundException.class);
        verify(cartRepository, never()).findByMemberIdWithItems(any());
        verify(cartRepository, never()).save(any(Cart.class));
    }
}
