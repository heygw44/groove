package com.groove.order.domain;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.InvalidOrderOwnershipException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order 도메인 — 생성/항목 추가/상태 전이")
class OrderTest {

    private static final OrderShippingInfo SHIPPING =
            new OrderShippingInfo("김철수", "01012345678", "서울시 강남구 테헤란로 123", "456호", "06234", false);

    private Album album(long id, long price) {
        Artist artist = Artist.create("A", null);
        Genre genre = Genre.create("G");
        Label label = Label.create("L");
        Album a = Album.create("Title-" + id, artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, price, 100,
                AlbumStatus.SELLING, false, null, null);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Nested
    @DisplayName("생성 (placeForMember / placeForGuest)")
    class Create {

        @Test
        @DisplayName("placeForMember — 회원 주문 PENDING 으로 시작")
        void member_startsPending() {
            Order order = Order.placeForMember("ORD-1", 7L, SHIPPING);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getMemberId()).isEqualTo(7L);
            assertThat(order.getGuestEmail()).isNull();
            assertThat(order.isGuestOrder()).isFalse();
            assertThat(order.getTotalAmount()).isZero();
        }

        @Test
        @DisplayName("placeForGuest — 게스트 주문 PENDING 으로 시작")
        void guest_startsPending() {
            Order order = Order.placeForGuest("ORD-2", "guest@example.com", "010-1111-2222", SHIPPING);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getMemberId()).isNull();
            assertThat(order.getGuestEmail()).isEqualTo("guest@example.com");
            assertThat(order.getGuestPhone()).isEqualTo("010-1111-2222");
            assertThat(order.isGuestOrder()).isTrue();
        }

        @Test
        @DisplayName("placeForMember — null memberId → InvalidOrderOwnershipException")
        void member_rejectsNullMemberId() {
            assertThatThrownBy(() -> Order.placeForMember("ORD-1", null, SHIPPING))
                    .isInstanceOf(InvalidOrderOwnershipException.class);
        }

        @Test
        @DisplayName("placeForGuest — 빈/null email → InvalidOrderOwnershipException")
        void guest_rejectsBlankEmail() {
            assertThatThrownBy(() -> Order.placeForGuest("ORD-1", null, null, SHIPPING))
                    .isInstanceOf(InvalidOrderOwnershipException.class);
            assertThatThrownBy(() -> Order.placeForGuest("ORD-1", " ", null, SHIPPING))
                    .isInstanceOf(InvalidOrderOwnershipException.class);
        }

        @Test
        @DisplayName("정적 팩토리 — 빈 orderNumber 거부")
        void rejectsBlankOrderNumber() {
            assertThatThrownBy(() -> Order.placeForMember("", 1L, SHIPPING))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Order.placeForGuest(" ", "g@e.com", null, SHIPPING))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("정적 팩토리 — null 배송지 거부")
        void rejectsNullShipping() {
            assertThatThrownBy(() -> Order.placeForMember("ORD-1", 1L, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Order.placeForGuest("ORD-1", "g@e.com", null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("placeForMember — 배송지 스냅샷 보존 (getShippingInfo)")
        void member_capturesShippingSnapshot() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);

            assertThat(order.getShippingInfo()).isEqualTo(SHIPPING);
        }
    }

    @Nested
    @DisplayName("addItem — totalAmount 누적")
    class AddItem {

        @Test
        @DisplayName("addItem — totalAmount 가 subtotal 합으로 누적")
        void totalAmount_accumulates() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);

            order.addItem(OrderItem.create(album(10L, 30000L), 2));
            order.addItem(OrderItem.create(album(11L, 15000L), 3));

            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getTotalAmount()).isEqualTo(2 * 30000L + 3 * 15000L);
        }

        @Test
        @DisplayName("addItem — order 역방향 연관 자동 주입")
        void addItem_attachesOrder() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);
            OrderItem item = OrderItem.create(album(10L, 1000L), 1);

            order.addItem(item);

            assertThat(item.getOrder()).isSameAs(order);
        }

        @Test
        @DisplayName("addItem — null 거부")
        void rejectsNull() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);

            assertThatThrownBy(() -> order.addItem(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("getItems — 외부에서 mutate 불가 (immutable view)")
        void getItems_isUnmodifiable() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);
            order.addItem(OrderItem.create(album(10L, 1000L), 1));

            assertThatThrownBy(() -> order.getItems().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("changeStatus — 합법/불법 전이 + 시각 기록")
    class ChangeStatus {

        @Test
        @DisplayName("PENDING → PAID — paidAt 기록")
        void pendingToPaid_recordsPaidAt() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);

            order.changeStatus(OrderStatus.PAID, null);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getPaidAt()).isNotNull();
            assertThat(order.getCancelledAt()).isNull();
        }

        @Test
        @DisplayName("PENDING → CANCELLED — cancelledAt + reason 기록")
        void pendingToCancelled_recordsCancelledMeta() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);

            order.changeStatus(OrderStatus.CANCELLED, "변심");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getCancelledAt()).isNotNull();
            assertThat(order.getCancelledReason()).isEqualTo("변심");
            assertThat(order.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("PENDING → PAYMENT_FAILED — paidAt/cancelledAt 모두 미기록")
        void pendingToPaymentFailed_recordsNeitherTimestamp() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);

            order.changeStatus(OrderStatus.PAYMENT_FAILED, null);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
            assertThat(order.getPaidAt()).isNull();
            assertThat(order.getCancelledAt()).isNull();
        }

        @Test
        @DisplayName("PAID 외 전이에서 reason 인자는 무시")
        void reason_ignoredOnNonCancelTransitions() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);

            order.changeStatus(OrderStatus.PAID, "ignored-reason");

            assertThat(order.getCancelledReason()).isNull();
        }

        @Test
        @DisplayName("불법 전이 (PENDING → SHIPPED) → IllegalStateTransitionException")
        void illegal_throws() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);

            assertThatThrownBy(() -> order.changeStatus(OrderStatus.SHIPPED, null))
                    .isInstanceOf(IllegalStateTransitionException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("종착 상태 (CANCELLED) 에서 어떤 전이도 거부")
        void terminal_rejectsAll() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);
            order.changeStatus(OrderStatus.CANCELLED, "초기 취소");

            for (OrderStatus next : OrderStatus.values()) {
                assertThatThrownBy(() -> order.changeStatus(next, null))
                        .as("CANCELLED -> %s", next)
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }

        @Test
        @DisplayName("자기 자신으로의 전이는 불법 (멱등성 보호)")
        void selfTransition_rejected() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);

            assertThatThrownBy(() -> order.changeStatus(OrderStatus.PENDING, null))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("CANCELLED 전이 후에도 totalAmount 는 보존 (취소 주문 금액 이력 유지)")
        void totalAmount_preservedAfterCancellation() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);
            order.addItem(OrderItem.create(album(10L, 30000L), 2));
            long beforeCancel = order.getTotalAmount();

            order.changeStatus(OrderStatus.CANCELLED, "변심");

            assertThat(order.getTotalAmount()).isEqualTo(beforeCancel);
            assertThat(order.getTotalAmount()).isEqualTo(60000L);
        }

        @Test
        @DisplayName("changeStatus(null) → NullPointerException (canTransitionTo 의 null-흡수와 책임 분리)")
        void rejectsNullNext() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);

            assertThatThrownBy(() -> order.changeStatus(null, null))
                    .isInstanceOf(NullPointerException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("정상 라이프사이클 PENDING → PAID → PREPARING → SHIPPED → DELIVERED → COMPLETED")
        void happyPath_walkthrough() {
            Order order = Order.placeForMember("ORD-1", 1L, SHIPPING);

            order.changeStatus(OrderStatus.PAID, null);
            order.changeStatus(OrderStatus.PREPARING, null);
            order.changeStatus(OrderStatus.SHIPPED, null);
            order.changeStatus(OrderStatus.DELIVERED, null);
            order.changeStatus(OrderStatus.COMPLETED, null);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(order.getPaidAt()).isNotNull();
            assertThat(order.getCancelledAt()).isNull();
        }
    }
}
