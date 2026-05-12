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
import com.groove.support.OrderFixtures;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.InsufficientStockException;
import com.groove.order.exception.InvalidOrderOwnershipException;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.OrderNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
        return new OrderCreateRequest(List.of(items), null, OrderFixtures.sampleShippingInfoRequest());
    }

    private OrderCreateRequest guestRequest(GuestInfoRequest guest, OrderItemRequest... items) {
        return new OrderCreateRequest(List.of(items), guest, OrderFixtures.sampleShippingInfoRequest());
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

    // ---------- findForMember ----------

    @Test
    @DisplayName("findForMember — 본인 주문이면 반환")
    void findForMember_ownOrder_returns() {
        Order order = OrderFixtures.memberOrder("ORD-1", 1L);
        given(orderRepository.findByOrderNumber("ORD-1")).willReturn(Optional.of(order));

        Order result = orderService.findForMember(1L, "ORD-1");

        assertThat(result).isSameAs(order);
    }

    @Test
    @DisplayName("findForMember — 미존재 주문이면 OrderNotFoundException(404)")
    void findForMember_missing_throwsNotFound() {
        given(orderRepository.findByOrderNumber("ORD-X")).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findForMember(1L, "ORD-X"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("findForMember — 타 회원 주문이면 OrderNotFoundException (존재 노출 회피)")
    void findForMember_otherMembersOrder_throwsNotFound() {
        Order order = OrderFixtures.memberOrder("ORD-1", 2L);
        given(orderRepository.findByOrderNumber("ORD-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.findForMember(1L, "ORD-1"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("findForMember — 게스트 주문에 회원으로 접근하면 404")
    void findForMember_guestOrder_throwsNotFound() {
        Order order = OrderFixtures.guestOrder("ORD-G", "g@x.com", null);
        given(orderRepository.findByOrderNumber("ORD-G")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.findForMember(1L, "ORD-G"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ---------- findForGuest ----------

    @Test
    @DisplayName("findForGuest — email 매칭 시 반환")
    void findForGuest_matchedEmail_returns() {
        Order order = OrderFixtures.guestOrder("ORD-G", "g@x.com", null);
        given(orderRepository.findByOrderNumber("ORD-G")).willReturn(Optional.of(order));

        Order result = orderService.findForGuest("ORD-G", "g@x.com");

        assertThat(result).isSameAs(order);
    }

    @Test
    @DisplayName("findForGuest — email 불일치 시 OrderNotFoundException")
    void findForGuest_emailMismatch_throwsNotFound() {
        Order order = OrderFixtures.guestOrder("ORD-G", "g@x.com", null);
        given(orderRepository.findByOrderNumber("ORD-G")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.findForGuest("ORD-G", "other@x.com"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("findForGuest — 회원 주문에 게스트로 접근하면 404")
    void findForGuest_memberOrder_throwsNotFound() {
        Order order = OrderFixtures.memberOrder("ORD-1", 1L);
        given(orderRepository.findByOrderNumber("ORD-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.findForGuest("ORD-1", "g@x.com"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("findForGuest — 미존재 orderNumber 면 404")
    void findForGuest_missing_throwsNotFound() {
        given(orderRepository.findByOrderNumber("ORD-X")).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findForGuest("ORD-X", "g@x.com"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ---------- listForMember ----------

    @Test
    @DisplayName("listForMember — status null 이면 회원 전체 주문 페이지 반환")
    void listForMember_noStatus_delegatesToFindByMemberId() {
        Pageable pageable = PageRequest.of(0, 20);
        Order o = OrderFixtures.memberOrder("ORD-1", 1L);
        Page<Order> page = new PageImpl<>(List.of(o), pageable, 1);
        given(orderRepository.findByMemberId(1L, pageable)).willReturn(page);

        Page<Order> result = orderService.listForMember(1L, null, pageable);

        assertThat(result.getContent()).containsExactly(o);
        verify(orderRepository, never()).findByMemberIdAndStatus(any(), any(), any());
    }

    @Test
    @DisplayName("listForMember — status 지정 시 status 필터 메서드로 위임")
    void listForMember_withStatus_delegatesToFindByMemberIdAndStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        Order o = OrderFixtures.memberOrder("ORD-1", 1L);
        Page<Order> page = new PageImpl<>(List.of(o), pageable, 1);
        given(orderRepository.findByMemberIdAndStatus(1L, OrderStatus.PENDING, pageable))
                .willReturn(page);

        Page<Order> result = orderService.listForMember(1L, OrderStatus.PENDING, pageable);

        assertThat(result.getContent()).containsExactly(o);
        verify(orderRepository, never()).findByMemberId(any(), any());
    }

    // ---------- cancel ----------

    @Test
    @DisplayName("cancel — PENDING 회원 주문 취소 시 상태 CANCELLED + 재고 복원")
    void cancel_member_pending_restoresStock() {
        Album a1 = album(10L, AlbumStatus.SELLING, 98, 30000L);
        Album a2 = album(11L, AlbumStatus.SELLING, 47, 15000L);
        Order order = OrderFixtures.memberOrder("ORD-1", 1L);
        order.addItem(OrderItem.create(a1, 2));
        order.addItem(OrderItem.create(a2, 3));
        given(orderRepository.findWithAlbumsByOrderNumber("ORD-1")).willReturn(Optional.of(order));

        Order result = orderService.cancel(1L, "ORD-1", "단순 변심");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getCancelledAt()).isNotNull();
        assertThat(result.getCancelledReason()).isEqualTo("단순 변심");
        assertThat(a1.getStock()).isEqualTo(100);
        assertThat(a2.getStock()).isEqualTo(50);
    }

    @Test
    @DisplayName("cancel — reason null 허용")
    void cancel_nullReason_ok() {
        Album a = album(10L, AlbumStatus.SELLING, 99, 30000L);
        Order order = OrderFixtures.memberOrder("ORD-1", 1L);
        order.addItem(OrderItem.create(a, 1));
        given(orderRepository.findWithAlbumsByOrderNumber("ORD-1")).willReturn(Optional.of(order));

        Order result = orderService.cancel(1L, "ORD-1", null);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getCancelledReason()).isNull();
        assertThat(a.getStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("cancel — 미존재 주문이면 OrderNotFoundException")
    void cancel_missing_throwsNotFound() {
        given(orderRepository.findWithAlbumsByOrderNumber("ORD-X")).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancel(1L, "ORD-X", null))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("cancel — 타 회원 주문이면 OrderNotFoundException (404)")
    void cancel_otherMembersOrder_throwsNotFound() {
        Order order = OrderFixtures.memberOrder("ORD-1", 2L);
        given(orderRepository.findWithAlbumsByOrderNumber("ORD-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(1L, "ORD-1", null))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("cancel — PENDING 외 상태(PAID)는 IllegalStateTransitionException(409)")
    void cancel_nonPending_throwsConflict() {
        Album a = album(10L, AlbumStatus.SELLING, 99, 30000L);
        Order order = OrderFixtures.memberOrder("ORD-1", 1L);
        order.addItem(OrderItem.create(a, 1));
        order.changeStatus(OrderStatus.PAID, null);
        given(orderRepository.findWithAlbumsByOrderNumber("ORD-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(1L, "ORD-1", null))
                .isInstanceOf(IllegalStateTransitionException.class);
        assertThat(a.getStock()).isEqualTo(99);
    }

    @Test
    @DisplayName("cancel — 이미 CANCELLED 면 409 (멱등 아님)")
    void cancel_alreadyCancelled_throws() {
        Album a = album(10L, AlbumStatus.SELLING, 99, 30000L);
        Order order = OrderFixtures.memberOrder("ORD-1", 1L);
        order.addItem(OrderItem.create(a, 1));
        order.changeStatus(OrderStatus.CANCELLED, "first");
        given(orderRepository.findWithAlbumsByOrderNumber("ORD-1")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(1L, "ORD-1", null))
                .isInstanceOf(IllegalStateTransitionException.class);
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
