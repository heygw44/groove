package com.groove.order.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.cart.exception.AlbumNotPurchasableException;
import com.groove.order.api.dto.GuestInfoRequest;
import com.groove.order.api.dto.OrderCreateRequest;
import com.groove.order.api.dto.OrderItemRequest;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.InsufficientStockException;
import com.groove.order.exception.InvalidOrderOwnershipException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private AlbumRepository albumRepository;
    @Mock
    private OrderNumberGenerator orderNumberGenerator;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, albumRepository, orderNumberGenerator);
    }

    private Album album(long id, AlbumStatus status, int stock, long price) {
        Artist artist = Artist.create("Artist-" + id, null);
        Genre genre = Genre.create("Genre-" + id);
        Label label = Label.create("Label-" + id);
        Album album = Album.create("Title-" + id, artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, price, stock,
                status, false, null, null);
        ReflectionTestUtils.setField(album, "id", id);
        return album;
    }

    private OrderCreateRequest memberRequest(OrderItemRequest... items) {
        return new OrderCreateRequest(List.of(items), null);
    }

    private OrderCreateRequest guestRequest(GuestInfoRequest guest, OrderItemRequest... items) {
        return new OrderCreateRequest(List.of(items), guest);
    }

    @Test
    @DisplayName("회원 주문 정상 생성 — items 누적·스냅샷·재고차감·PENDING 상태")
    void place_member_success() {
        Album a1 = album(10L, AlbumStatus.SELLING, 100, 30000L);
        Album a2 = album(11L, AlbumStatus.SELLING, 50, 15000L);
        given(albumRepository.findById(10L)).willReturn(Optional.of(a1));
        given(albumRepository.findById(11L)).willReturn(Optional.of(a2));
        given(orderNumberGenerator.generate()).willReturn("ORD-20260508-A1B2C3");
        given(orderRepository.existsByOrderNumber("ORD-20260508-A1B2C3")).willReturn(false);
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        Order order = orderService.place(1L, memberRequest(
                new OrderItemRequest(10L, 2),
                new OrderItemRequest(11L, 3)));

        assertThat(order.getOrderNumber()).isEqualTo("ORD-20260508-A1B2C3");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getMemberId()).isEqualTo(1L);
        assertThat(order.isGuestOrder()).isFalse();
        assertThat(order.getTotalAmount()).isEqualTo(30000L * 2 + 15000L * 3);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getItems().get(0).getAlbumTitleSnapshot()).isEqualTo("Title-10");
        assertThat(order.getItems().get(0).getUnitPrice()).isEqualTo(30000L);
        assertThat(a1.getStock()).isEqualTo(98);
        assertThat(a2.getStock()).isEqualTo(47);
    }

    @Test
    @DisplayName("게스트 주문 정상 생성 — guestEmail/Phone 보존, memberId null")
    void place_guest_success() {
        Album a1 = album(10L, AlbumStatus.SELLING, 100, 30000L);
        given(albumRepository.findById(10L)).willReturn(Optional.of(a1));
        given(orderNumberGenerator.generate()).willReturn("ORD-20260508-G1G1G1");
        given(orderRepository.existsByOrderNumber("ORD-20260508-G1G1G1")).willReturn(false);
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        Order order = orderService.place(null, guestRequest(
                new GuestInfoRequest("guest@example.com", "01012345678"),
                new OrderItemRequest(10L, 1)));

        assertThat(order.isGuestOrder()).isTrue();
        assertThat(order.getGuestEmail()).isEqualTo("guest@example.com");
        assertThat(order.getGuestPhone()).isEqualTo("01012345678");
        assertThat(order.getMemberId()).isNull();
    }

    @Test
    @DisplayName("memberId·guest 동시 지정 → InvalidOrderOwnershipException (422)")
    void place_bothMemberAndGuest_rejected() {
        OrderCreateRequest req = guestRequest(
                new GuestInfoRequest("g@x.com", "01012345678"),
                new OrderItemRequest(10L, 1));

        assertThatThrownBy(() -> orderService.place(1L, req))
                .isInstanceOf(InvalidOrderOwnershipException.class);
        verify(albumRepository, never()).findById(any());
    }

    @Test
    @DisplayName("memberId 없고 guest 도 없으면 InvalidOrderOwnershipException (422)")
    void place_neitherMemberNorGuest_rejected() {
        OrderCreateRequest req = memberRequest(new OrderItemRequest(10L, 1));

        assertThatThrownBy(() -> orderService.place(null, req))
                .isInstanceOf(InvalidOrderOwnershipException.class);
    }

    @Test
    @DisplayName("album 미존재 → AlbumNotFoundException")
    void place_albumNotFound() {
        given(albumRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.place(1L, memberRequest(
                new OrderItemRequest(99L, 1))))
                .isInstanceOf(AlbumNotFoundException.class);
    }

    @Test
    @DisplayName("album.status = HIDDEN → AlbumNotPurchasableException")
    void place_hiddenRejected() {
        Album a = album(10L, AlbumStatus.HIDDEN, 100, 30000L);
        given(albumRepository.findById(10L)).willReturn(Optional.of(a));

        assertThatThrownBy(() -> orderService.place(1L, memberRequest(
                new OrderItemRequest(10L, 1))))
                .isInstanceOf(AlbumNotPurchasableException.class);
    }

    @Test
    @DisplayName("album.status = SOLD_OUT → AlbumNotPurchasableException")
    void place_soldOutRejected() {
        Album a = album(10L, AlbumStatus.SOLD_OUT, 100, 30000L);
        given(albumRepository.findById(10L)).willReturn(Optional.of(a));

        assertThatThrownBy(() -> orderService.place(1L, memberRequest(
                new OrderItemRequest(10L, 1))))
                .isInstanceOf(AlbumNotPurchasableException.class);
    }

    @Test
    @DisplayName("재고 부족 → InsufficientStockException, 재고 미차감")
    void place_insufficientStock() {
        Album a = album(10L, AlbumStatus.SELLING, 2, 30000L);
        given(albumRepository.findById(10L)).willReturn(Optional.of(a));

        assertThatThrownBy(() -> orderService.place(1L, memberRequest(
                new OrderItemRequest(10L, 5))))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(a.getStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("orderNumber 가 이미 존재하면 다음 후보로 재발급 (최대 3회)")
    void place_skipsExistingOrderNumber() {
        Album a = album(10L, AlbumStatus.SELLING, 10, 30000L);
        given(albumRepository.findById(10L)).willReturn(Optional.of(a));
        given(orderNumberGenerator.generate())
                .willReturn("ORD-20260508-AAAAAA", "ORD-20260508-BBBBBB", "ORD-20260508-CCCCCC");
        given(orderRepository.existsByOrderNumber("ORD-20260508-AAAAAA")).willReturn(true);
        given(orderRepository.existsByOrderNumber("ORD-20260508-BBBBBB")).willReturn(false);
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        Order order = orderService.place(1L, memberRequest(new OrderItemRequest(10L, 1)));

        assertThat(order.getOrderNumber()).isEqualTo("ORD-20260508-BBBBBB");
    }
}
