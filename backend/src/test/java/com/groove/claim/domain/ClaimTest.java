package com.groove.claim.domain;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.claim.exception.ClaimInvalidStateTransitionException;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.support.OrderFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Claim 도메인")
class ClaimTest {

    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    private Order orderWithItem(long unitPrice, int quantity) {
        Album album = Album.create("Album", Artist.create("A", null), Genre.create("Rock"), Label.create("L"),
                (short) 2020, AlbumFormat.LP_12, unitPrice, 100, AlbumStatus.SELLING, false, null, null);
        Order order = OrderFixtures.memberOrder("ORD-20260612-AAAAAA", 1L);
        order.addItem(OrderItem.create(album, quantity));
        return order;
    }

    private Claim requestedClaim() {
        return Claim.request(OrderFixtures.memberOrder("ORD-20260612-BBBBBB", 1L), "단순 변심");
    }

    @Test
    @DisplayName("request: REQUESTED 로 생성하고 사유를 보존한다")
    void request_createsRequested() {
        Claim claim = requestedClaim();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REQUESTED);
        assertThat(claim.getReason()).isEqualTo("단순 변심");
        assertThat(claim.getRefundAmount()).isZero();
        assertThat(claim.getItems()).isEmpty();
    }

    @Test
    @DisplayName("request: 사유 blank·order null 은 거부한다")
    void request_rejectsInvalid() {
        Order order = OrderFixtures.memberOrder("ORD-20260612-CCCCCC", 1L);
        assertThatThrownBy(() -> Claim.request(order, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Claim.request(null, "사유")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("addItem + getGross: 항목 정가 합을 누적한다")
    void addItem_accumulatesGross() {
        Order order = orderWithItem(15_000L, 2);
        OrderItem item = order.getItems().get(0);
        Claim claim = Claim.request(order, "변심");

        claim.addItem(ClaimItem.of(item, 2));

        assertThat(claim.getItems()).hasSize(1);
        assertThat(claim.getGross()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("정상 전이: REQUESTED→APPROVED→IN_TRANSIT→INSPECTING→REFUNDED 가 시각을 기록한다")
    void happyPathTransitions_recordTimestamps() {
        Claim claim = requestedClaim();

        claim.approve(NOW);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getApprovedAt()).isEqualTo(NOW);

        claim.startTransit(NOW);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.IN_TRANSIT);
        assertThat(claim.getInTransitAt()).isEqualTo(NOW);

        claim.startInspecting(NOW);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.INSPECTING);
        assertThat(claim.getInspectingAt()).isEqualTo(NOW);

        claim.markRefunded(12_000L, NOW);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REFUNDED);
        assertThat(claim.getRefundAmount()).isEqualTo(12_000L);
        assertThat(claim.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("reject: REQUESTED 에서 사유·종착 시각을 기록하며 REJECTED 로")
    void reject_recordsReasonAndTimestamp() {
        Claim claim = requestedClaim();

        claim.reject("재고 없음", NOW);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.getRejectionReason()).isEqualTo("재고 없음");
        assertThat(claim.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("reject: INSPECTING(검수 불합격)에서도 REJECTED 가능")
    void reject_fromInspecting() {
        Claim claim = requestedClaim();
        claim.approve(NOW);
        claim.startTransit(NOW);
        claim.startInspecting(NOW);

        claim.reject("사용 흔적", NOW);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
    }

    @Test
    @DisplayName("불법 전이: REQUESTED 에서 곧장 markRefunded 는 ClaimInvalidStateTransitionException")
    void illegalTransition_throws() {
        Claim claim = requestedClaim();

        assertThatThrownBy(() -> claim.markRefunded(1_000L, NOW))
                .isInstanceOf(ClaimInvalidStateTransitionException.class);
        assertThatThrownBy(() -> claim.startTransit(NOW))
                .isInstanceOf(ClaimInvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("markRefunded: 음수 환불액은 IllegalArgumentException")
    void markRefunded_rejectsNegative() {
        Claim claim = requestedClaim();
        claim.approve(NOW);
        claim.startTransit(NOW);
        claim.startInspecting(NOW);

        assertThatThrownBy(() -> claim.markRefunded(-1L, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("종착(REFUNDED) 후 추가 전이는 거부")
    void terminal_noFurtherTransition() {
        Claim claim = requestedClaim();
        claim.approve(NOW);
        claim.startTransit(NOW);
        claim.startInspecting(NOW);
        claim.markRefunded(1_000L, NOW);

        assertThatThrownBy(() -> claim.reject("x", NOW))
                .isInstanceOf(ClaimInvalidStateTransitionException.class);
    }

    // --- CANCEL 타입 (#238) ---------------------------------------------------

    @Test
    @DisplayName("request: 기본 타입은 RETURN (반품)")
    void request_defaultsToReturnType() {
        assertThat(requestedClaim().getClaimType()).isEqualTo(ClaimType.RETURN);
    }

    @Test
    @DisplayName("requestCancellation: CANCEL 타입·REQUESTED 로 생성한다")
    void requestCancellation_createsCancelRequested() {
        Claim claim = Claim.requestCancellation(OrderFixtures.memberOrder("ORD-20260612-DDDDDD", 1L), "부분 취소");

        assertThat(claim.getClaimType()).isEqualTo(ClaimType.CANCEL);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REQUESTED);
        assertThat(claim.getReason()).isEqualTo("부분 취소");
    }

    @Test
    @DisplayName("markCancelRefunded: CANCEL 은 REQUESTED→REFUNDED 1-스텝으로 환불 확정")
    void markCancelRefunded_requestedToRefunded() {
        Claim claim = Claim.requestCancellation(OrderFixtures.memberOrder("ORD-20260612-EEEEEE", 1L), "부분 취소");

        claim.markCancelRefunded(9_000L, NOW);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REFUNDED);
        assertThat(claim.getRefundAmount()).isEqualTo(9_000L);
        assertThat(claim.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("markCancelRefunded: RETURN 타입에 호출하면 ClaimInvalidStateTransitionException")
    void markCancelRefunded_rejectsReturnType() {
        Claim claim = requestedClaim(); // RETURN

        assertThatThrownBy(() -> claim.markCancelRefunded(1_000L, NOW))
                .isInstanceOf(ClaimInvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("markCancelRefunded: 이미 REFUNDED 면(REQUESTED 아님) 거부 — 중복 환불 방지")
    void markCancelRefunded_rejectsWhenNotRequested() {
        Claim claim = Claim.requestCancellation(OrderFixtures.memberOrder("ORD-20260612-FFFFFF", 1L), "부분 취소");
        claim.markCancelRefunded(1_000L, NOW);

        assertThatThrownBy(() -> claim.markCancelRefunded(1_000L, NOW))
                .isInstanceOf(ClaimInvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("markCancelRefunded: 음수 환불액은 IllegalArgumentException")
    void markCancelRefunded_rejectsNegative() {
        Claim claim = Claim.requestCancellation(OrderFixtures.memberOrder("ORD-20260612-GGGGGG", 1L), "부분 취소");

        assertThatThrownBy(() -> claim.markCancelRefunded(-1L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
