package com.groove.order.domain;

import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.support.MemberFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("OrderRepository 통합 테스트 (Testcontainers MySQL)")
class OrderRepositoryTest {

    private static final Set<OrderStatus> WITHDRAWAL_BLOCKING =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);

    private static final Set<OrderStatus> NON_SHIPPING_TERMINAL =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED);

    private static final long OTHER_MEMBER_ID = 9_999L;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MemberRepository memberRepository;

    // orders.member_id → member.id FK 때문에 실제 회원이 선행 존재해야 한다.
    private Long memberId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.saveAndFlush(
                MemberFixtures.register("order-repo-test@example.com", "$2a$12$hash", "주문자", "01012345678"));
        memberId = member.getId();
    }

    private static OrderShippingInfo shipping() {
        return new OrderShippingInfo("김철수", "01012345678", "서울시 강남구", "101동 202호", "06000", false);
    }

    private Order saveOrder(Long memberId, OrderStatus target) {
        Order order = Order.placeForMember("ORD-" + System.nanoTime(), memberId, shipping());
        // PENDING → target 으로 단계 전이 (canTransitionTo 규칙 준수)
        switch (target) {
            case PENDING -> { /* 이미 PENDING */ }
            case PAID -> order.changeStatus(OrderStatus.PAID, null);
            case PREPARING -> {
                order.changeStatus(OrderStatus.PAID, null);
                order.changeStatus(OrderStatus.PREPARING, null);
            }
            case SHIPPED -> {
                order.changeStatus(OrderStatus.PAID, null);
                order.changeStatus(OrderStatus.PREPARING, null);
                order.changeStatus(OrderStatus.SHIPPED, null);
            }
            case DELIVERED -> {
                order.changeStatus(OrderStatus.PAID, null);
                order.changeStatus(OrderStatus.PREPARING, null);
                order.changeStatus(OrderStatus.SHIPPED, null);
                order.changeStatus(OrderStatus.DELIVERED, null);
            }
            case CANCELLED -> order.changeStatus(OrderStatus.CANCELLED, "테스트");
            case PAYMENT_FAILED -> order.changeStatus(OrderStatus.PAYMENT_FAILED, null);
            default -> throw new IllegalArgumentException("미지원 상태: " + target);
        }
        return orderRepository.saveAndFlush(order);
    }

    @Test
    @DisplayName("PAID 주문 존재 → true (진행 중 주문으로 탈퇴 차단)")
    void existsByMemberIdAndStatusIn_paid_true() {
        saveOrder(memberId, OrderStatus.PAID);

        assertThat(orderRepository.existsByMemberIdAndStatusIn(memberId, WITHDRAWAL_BLOCKING)).isTrue();
    }

    @Test
    @DisplayName("PREPARING 주문 존재 → true")
    void existsByMemberIdAndStatusIn_preparing_true() {
        saveOrder(memberId, OrderStatus.PREPARING);

        assertThat(orderRepository.existsByMemberIdAndStatusIn(memberId, WITHDRAWAL_BLOCKING)).isTrue();
    }

    @Test
    @DisplayName("SHIPPED 주문 존재 → true")
    void existsByMemberIdAndStatusIn_shipped_true() {
        saveOrder(memberId, OrderStatus.SHIPPED);

        assertThat(orderRepository.existsByMemberIdAndStatusIn(memberId, WITHDRAWAL_BLOCKING)).isTrue();
    }

    @Test
    @DisplayName("PENDING(미결제)만 존재 → false (차단 대상 아님)")
    void existsByMemberIdAndStatusIn_pendingOnly_false() {
        saveOrder(memberId, OrderStatus.PENDING);

        assertThat(orderRepository.existsByMemberIdAndStatusIn(memberId, WITHDRAWAL_BLOCKING)).isFalse();
    }

    @Test
    @DisplayName("종착 상태(DELIVERED/CANCELLED)만 존재 → false")
    void existsByMemberIdAndStatusIn_terminalOnly_false() {
        saveOrder(memberId, OrderStatus.DELIVERED);
        saveOrder(memberId, OrderStatus.CANCELLED);

        assertThat(orderRepository.existsByMemberIdAndStatusIn(memberId, WITHDRAWAL_BLOCKING)).isFalse();
    }

    @Test
    @DisplayName("주문 없음 → false")
    void existsByMemberIdAndStatusIn_noOrders_false() {
        assertThat(orderRepository.existsByMemberIdAndStatusIn(memberId, WITHDRAWAL_BLOCKING)).isFalse();
    }

    @Test
    @DisplayName("다른 회원의 PAID 주문은 영향 없음 → false")
    void existsByMemberIdAndStatusIn_otherMember_false() {
        saveOrder(memberId, OrderStatus.PAID);

        assertThat(orderRepository.existsByMemberIdAndStatusIn(OTHER_MEMBER_ID, WITHDRAWAL_BLOCKING)).isFalse();
    }

    @Test
    @DisplayName("비배송 종착 익명화 대상(#188) — PENDING/PAYMENT_FAILED/CANCELLED 만 조회, 진행/배송완료 주문은 제외")
    void findTerminalNonShipping_selectsTerminalExcludesActive() {
        Order pending = saveOrder(memberId, OrderStatus.PENDING);
        Order paymentFailed = saveOrder(memberId, OrderStatus.PAYMENT_FAILED);
        Order cancelled = saveOrder(memberId, OrderStatus.CANCELLED);
        saveOrder(memberId, OrderStatus.PAID);       // 진행 중 — 제외
        saveOrder(memberId, OrderStatus.DELIVERED);  // 배송완료 배치 담당 — 제외

        // updated_at 은 @LastModifiedDate 라 과거로 못 박으므로 cutoff 를 미래로 잡아 방금 저장된 건이 모두 cutoff 이전이 되게 한다.
        Instant cutoff = Instant.now().plus(Duration.ofHours(1));
        List<OrderRepository.OrderNumberView> targets =
                orderRepository.findByStatusInAndAnonymizedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        NON_SHIPPING_TERMINAL, cutoff, Limit.of(100));

        assertThat(targets)
                .extracting(OrderRepository.OrderNumberView::getId)
                .containsExactlyInAnyOrder(pending.getId(), paymentFailed.getId(), cancelled.getId());
    }

    @Test
    @DisplayName("비배송 종착 익명화 대상(#188) — cutoff 이전(보존기간 미경과)이면 제외된다")
    void findTerminalNonShipping_recentlyUpdated_excluded() {
        saveOrder(memberId, OrderStatus.CANCELLED);

        // 방금 저장돼 updated_at ≈ now 인데 cutoff 를 과거로 잡으면 대상에서 빠져야 한다.
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        List<OrderRepository.OrderNumberView> targets =
                orderRepository.findByStatusInAndAnonymizedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        NON_SHIPPING_TERMINAL, cutoff, Limit.of(100));

        assertThat(targets).isEmpty();
    }

    @Test
    @DisplayName("비배송 종착 익명화 대상(#188) — 이미 익명화된(anonymized_at != null) 주문은 제외된다")
    void findTerminalNonShipping_alreadyAnonymized_excluded() {
        Order cancelled = saveOrder(memberId, OrderStatus.CANCELLED);
        cancelled.anonymizePii(Instant.now());
        orderRepository.saveAndFlush(cancelled);

        Instant cutoff = Instant.now().plus(Duration.ofHours(1));
        List<OrderRepository.OrderNumberView> targets =
                orderRepository.findByStatusInAndAnonymizedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        NON_SHIPPING_TERMINAL, cutoff, Limit.of(100));

        assertThat(targets).isEmpty();
    }
}
