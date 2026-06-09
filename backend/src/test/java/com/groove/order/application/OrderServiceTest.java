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
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private com.groove.coupon.application.CouponApplicationService couponApplicationService;
    @Mock
    private MemberRepository memberRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, albumRepository, orderNumberGenerator,
                couponApplicationService, memberRepository);
        // 활성 회원 기본값 — 가드(#187)는 회원 주문(memberId != null)일 때만 호출된다. 탈퇴 시나리오만 false 로 override 한다.
        lenient().when(memberRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);
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
        return new OrderCreateRequest(List.of(items), null, OrderFixtures.sampleShippingInfoRequest(), null);
    }

    private OrderCreateRequest memberRequestWithCoupon(Long memberCouponId, OrderItemRequest... items) {
        return new OrderCreateRequest(List.of(items), null, OrderFixtures.sampleShippingInfoRequest(),
                memberCouponId);
    }

    private OrderCreateRequest guestRequest(GuestInfoRequest guest, OrderItemRequest... items) {
        return new OrderCreateRequest(List.of(items), guest, OrderFixtures.sampleShippingInfoRequest(), null);
    }

    private OrderCreateRequest guestRequestWithCoupon(GuestInfoRequest guest, Long memberCouponId,
                                                       OrderItemRequest... items) {
        return new OrderCreateRequest(List.of(items), guest, OrderFixtures.sampleShippingInfoRequest(),
                memberCouponId);
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
        // 게스트 주문은 탈퇴 회원 가드(#187)를 거치지 않는다 — memberId == null 이라 회원 검사를 호출하지 않음.
        verify(memberRepository, never()).existsByIdAndDeletedAtIsNull(any());
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
    @DisplayName("회원 주문 — 탈퇴(soft delete) 회원이면 MemberNotFoundException, album 미조회·미저장 (#187)")
    void place_memberWithdrawn_throws() {
        long withdrawnMemberId = 99L;
        given(memberRepository.existsByIdAndDeletedAtIsNull(withdrawnMemberId)).willReturn(false);

        assertThatThrownBy(() -> orderService.place(withdrawnMemberId,
                memberRequest(new OrderItemRequest(10L, 1))))
                .isInstanceOf(MemberNotFoundException.class);
        verify(albumRepository, never()).findById(any());
        verify(orderRepository, never()).save(any(Order.class));
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
    @DisplayName("findForGuest — 익명화된 게스트 주문(guestEmail=null) 은 NPE 가 아니라 404 (#170)")
    void findForGuest_anonymizedOrder_throwsNotFoundNotNpe() {
        Order order = OrderFixtures.guestOrder("ORD-G", "g@x.com", null);
        order.anonymizePii(java.time.Instant.parse("2026-06-09T00:00:00Z")); // guestEmail → null
        given(orderRepository.findByOrderNumber("ORD-G")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.findForGuest("ORD-G", "g@x.com"))
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

    // -- 쿠폰 통합 (#91) ------------------------------------------------------

    @Test
    @DisplayName("게스트 + memberCouponId → CouponNotApplicableException, 재고/저장 미실행")
    void place_guestWithCoupon_rejected() {
        GuestInfoRequest guest = new GuestInfoRequest("g@example.com", "010-1111-2222");

        assertThatThrownBy(() -> orderService.place(null,
                guestRequestWithCoupon(guest, 7L, new OrderItemRequest(10L, 1))))
                .isInstanceOf(com.groove.coupon.exception.CouponNotApplicableException.class);
        verify(albumRepository, never()).findById(any());
        verify(orderRepository, never()).save(any(Order.class));
        verify(couponApplicationService, never()).applyToOrder(any(), any(), any());
    }

    @Test
    @DisplayName("회원 주문 + memberCouponId → 저장 후 applyToOrder 호출")
    void place_memberWithCoupon_callsApply() {
        Album a = album(10L, AlbumStatus.SELLING, 10, 30000L);
        given(albumRepository.findById(10L)).willReturn(Optional.of(a));
        given(orderNumberGenerator.generate()).willReturn("ORD-20260528-A1A1A1");
        given(orderRepository.existsByOrderNumber("ORD-20260528-A1A1A1")).willReturn(false);
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> {
            Order o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 555L);  // 저장 시 id 부여 흉내
            return o;
        });

        Order order = orderService.place(1L, memberRequestWithCoupon(7L, new OrderItemRequest(10L, 1)));

        assertThat(order.getId()).isEqualTo(555L);
        verify(couponApplicationService).applyToOrder(7L, 1L, order);
    }

    @Test
    @DisplayName("회원 주문 + memberCouponId == null → applyToOrder 미호출")
    void place_memberNoCoupon_doesNotApply() {
        Album a = album(10L, AlbumStatus.SELLING, 10, 30000L);
        given(albumRepository.findById(10L)).willReturn(Optional.of(a));
        given(orderNumberGenerator.generate()).willReturn("ORD-20260528-B2B2B2");
        given(orderRepository.existsByOrderNumber("ORD-20260528-B2B2B2")).willReturn(false);
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        orderService.place(1L, memberRequest(new OrderItemRequest(10L, 1)));

        verify(couponApplicationService, never()).applyToOrder(any(), any(), any());
    }

    @Test
    @DisplayName("cancel — 재고 복원 후 restoreForOrder 호출 (적용 여부 무관, 미적용은 서비스에서 no-op)")
    void cancel_callsCouponRestore() {
        Album a = album(10L, AlbumStatus.SELLING, 0, 30000L);
        Order order = Order.placeForMember("ORD-20260528-C3C3C3", 1L, OrderFixtures.sampleShippingInfo());
        order.addItem(com.groove.order.domain.OrderItem.create(a, 2));
        ReflectionTestUtils.setField(order, "id", 777L);
        given(orderRepository.findWithAlbumsByOrderNumber("ORD-20260528-C3C3C3"))
                .willReturn(Optional.of(order));

        orderService.cancel(1L, "ORD-20260528-C3C3C3", "변심");

        assertThat(order.getStatus()).isEqualTo(com.groove.order.domain.OrderStatus.CANCELLED);
        assertThat(a.getStock()).isEqualTo(2);   // 재고 복원
        verify(couponApplicationService).restoreForOrder(777L);
    }

    @Test
    @DisplayName("쿠폰 적용 실패 → 예외 전파 (Spring 트랜잭션 롤백 신호) — 재고/주문 정합성은 Spring 위임")
    void place_couponApplyFails_propagates() {
        Album a = album(10L, AlbumStatus.SELLING, 10, 30000L);
        given(albumRepository.findById(10L)).willReturn(Optional.of(a));
        given(orderNumberGenerator.generate()).willReturn("ORD-20260528-F4F4F4");
        given(orderRepository.existsByOrderNumber("ORD-20260528-F4F4F4")).willReturn(false);
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> {
            Order o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 888L);
            return o;
        });
        // applyToOrder 가 throw 하면 service 는 잡지 않고 그대로 전파해야 한다 — Spring 이 @Transactional 로 롤백.
        doThrow(new com.groove.coupon.exception.CouponAlreadyUsedException(7L))
                .when(couponApplicationService).applyToOrder(any(), any(), any());

        assertThatThrownBy(() -> orderService.place(1L,
                memberRequestWithCoupon(7L, new OrderItemRequest(10L, 1))))
                .isInstanceOf(com.groove.coupon.exception.CouponAlreadyUsedException.class);

        // applyToOrder 가 호출됐고, 그 위치는 save 이후이므로 호출 자체가 일어나야 한다.
        verify(couponApplicationService).applyToOrder(any(), any(), any());
        verify(orderRepository).save(any(Order.class));
    }
}
