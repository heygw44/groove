package com.groove.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrderStatus 상태 전이 매트릭스 전수 검증 (8×8 = 64 케이스).
 *
 * <p>합법 전이는 ARCHITECTURE.md §8 다이어그램 기준 12종이다. 본 테스트는
 * "합법 전이 표 + 종착 상태" 두 단으로 SSOT 를 두고 — 매트릭스의 나머지 셀은 모두 불법 (false) 이다.
 */
@DisplayName("OrderStatus — 전이 매트릭스 전수")
class OrderStatusTest {

    private static final Set<Pair> LEGAL = Set.of(
            new Pair(OrderStatus.PENDING, OrderStatus.PAID),
            new Pair(OrderStatus.PENDING, OrderStatus.PAYMENT_FAILED),
            new Pair(OrderStatus.PENDING, OrderStatus.CANCELLED),
            new Pair(OrderStatus.PAID, OrderStatus.PREPARING),
            new Pair(OrderStatus.PAID, OrderStatus.CANCELLED),
            new Pair(OrderStatus.PREPARING, OrderStatus.SHIPPED),
            new Pair(OrderStatus.PREPARING, OrderStatus.CANCELLED),
            new Pair(OrderStatus.SHIPPED, OrderStatus.DELIVERED),
            new Pair(OrderStatus.DELIVERED, OrderStatus.COMPLETED)
    );

    private static final Set<OrderStatus> TERMINAL = EnumSet.of(
            OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED
    );

    static Stream<Arguments> allTransitions() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                b.add(Arguments.of(from, to));
            }
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allTransitions")
    @DisplayName("64 케이스 매트릭스: 합법 전이 표에 포함된 9개만 true, 나머지는 false")
    void canTransitionTo_matrix(OrderStatus from, OrderStatus to) {
        boolean expected = LEGAL.contains(new Pair(from, to));

        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    @DisplayName("자기 자신으로의 전이는 모두 불법 (PENDING→PENDING 등)")
    void canTransitionTo_selfIsAlwaysIllegal() {
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(s.canTransitionTo(s))
                    .as("self transition %s -> %s", s, s)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("null 로의 전이는 false (NPE 대신 false 로 흡수)")
    void canTransitionTo_nullIsFalse() {
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(s.canTransitionTo(null)).isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("allStatuses")
    @DisplayName("isTerminal: COMPLETED/CANCELLED/PAYMENT_FAILED 만 true")
    void isTerminal(OrderStatus s) {
        assertThat(s.isTerminal()).isEqualTo(TERMINAL.contains(s));
    }

    static Stream<OrderStatus> allStatuses() {
        return Stream.of(OrderStatus.values());
    }

    @Test
    @DisplayName("종착 상태에서 어떤 상태로도 전이 불가")
    void terminalStates_noOutgoing() {
        for (OrderStatus terminal : TERMINAL) {
            for (OrderStatus next : OrderStatus.values()) {
                assertThat(terminal.canTransitionTo(next))
                        .as("%s -> %s should be illegal (terminal)", terminal, next)
                        .isFalse();
            }
        }
    }

    private record Pair(OrderStatus from, OrderStatus to) {
    }
}
