package com.groove.shipping.domain;

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
 * ShippingStatus 상태 전이 매트릭스 전수 검증 (4×4 = 16 케이스).
 *
 * <p>OrderStatus(8×8)·PaymentStatus(4×4) 와 동일한 패턴으로 통일한다 (#142) —
 * "합법 전이 표 + 종착 상태" 두 단을 SSOT 로 두고, 매트릭스의 나머지 셀은 모두 불법(false).
 * 합법 전이는 4종(PREPARING→SHIPPED, PREPARING→CANCELLED, SHIPPED→DELIVERED, SHIPPED→CANCELLED) — #233 에서 CANCELLED 추가.
 */
@DisplayName("ShippingStatus — 전이 매트릭스 전수")
class ShippingStatusTest {

    private static final Set<Pair> LEGAL = Set.of(
            new Pair(ShippingStatus.PREPARING, ShippingStatus.SHIPPED),
            new Pair(ShippingStatus.PREPARING, ShippingStatus.CANCELLED),
            new Pair(ShippingStatus.SHIPPED, ShippingStatus.DELIVERED),
            new Pair(ShippingStatus.SHIPPED, ShippingStatus.CANCELLED)
    );

    private static final Set<ShippingStatus> TERMINAL = EnumSet.of(ShippingStatus.DELIVERED, ShippingStatus.CANCELLED);

    static Stream<Arguments> allTransitions() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (ShippingStatus from : ShippingStatus.values()) {
            for (ShippingStatus to : ShippingStatus.values()) {
                b.add(Arguments.of(from, to));
            }
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allTransitions")
    @DisplayName("16 케이스 매트릭스: 합법 전이 표에 포함된 4종만 true, 나머지는 false")
    void canTransitionTo_matrix(ShippingStatus from, ShippingStatus to) {
        boolean expected = LEGAL.contains(new Pair(from, to));

        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    @DisplayName("자기 자신으로의 전이는 모두 불법")
    void canTransitionTo_selfIsAlwaysIllegal() {
        for (ShippingStatus s : ShippingStatus.values()) {
            assertThat(s.canTransitionTo(s))
                    .as("self transition %s -> %s", s, s)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("null 로의 전이는 false (NPE 대신 false 로 흡수)")
    void canTransitionTo_nullIsFalse() {
        for (ShippingStatus s : ShippingStatus.values()) {
            assertThat(s.canTransitionTo(null)).isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("allStatuses")
    @DisplayName("isTerminal: DELIVERED·CANCELLED 만 true")
    void isTerminal(ShippingStatus s) {
        assertThat(s.isTerminal()).isEqualTo(TERMINAL.contains(s));
    }

    static Stream<ShippingStatus> allStatuses() {
        return Stream.of(ShippingStatus.values());
    }

    @Test
    @DisplayName("종착 상태(DELIVERED·CANCELLED) 에서 어떤 상태로도 전이 불가")
    void terminalStates_noOutgoing() {
        for (ShippingStatus terminal : TERMINAL) {
            for (ShippingStatus next : ShippingStatus.values()) {
                assertThat(terminal.canTransitionTo(next))
                        .as("%s -> %s should be illegal (terminal)", terminal, next)
                        .isFalse();
            }
        }
    }

    private record Pair(ShippingStatus from, ShippingStatus to) {
    }
}
