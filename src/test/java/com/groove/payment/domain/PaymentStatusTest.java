package com.groove.payment.domain;

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
 * PaymentStatus 전이 매트릭스 전수 검증 (4×4 = 16 케이스).
 *
 * <p>합법 전이는 ERD §6 / glossary §3.5 기준 3종(PENDING→PAID, PENDING→FAILED, PAID→REFUNDED)이며,
 * 매트릭스의 나머지 셀은 모두 불법이다. {@code OrderStatusTest} 와 동일한 SSOT 2단 구조를 따른다.
 */
@DisplayName("PaymentStatus — 전이 매트릭스 전수")
class PaymentStatusTest {

    private static final Set<Pair> LEGAL = Set.of(
            new Pair(PaymentStatus.PENDING, PaymentStatus.PAID),
            new Pair(PaymentStatus.PENDING, PaymentStatus.FAILED),
            new Pair(PaymentStatus.PAID, PaymentStatus.REFUNDED)
    );

    private static final Set<PaymentStatus> TERMINAL = EnumSet.of(PaymentStatus.FAILED, PaymentStatus.REFUNDED);

    static Stream<Arguments> allTransitions() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (PaymentStatus from : PaymentStatus.values()) {
            for (PaymentStatus to : PaymentStatus.values()) {
                b.add(Arguments.of(from, to));
            }
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allTransitions")
    @DisplayName("16 케이스 매트릭스: 합법 전이 표에 포함된 3종만 true, 나머지는 false")
    void canTransitionTo_matrix(PaymentStatus from, PaymentStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(LEGAL.contains(new Pair(from, to)));
    }

    @Test
    @DisplayName("자기 자신으로의 전이는 모두 불법")
    void canTransitionTo_selfIsAlwaysIllegal() {
        for (PaymentStatus s : PaymentStatus.values()) {
            assertThat(s.canTransitionTo(s)).as("self transition %s", s).isFalse();
        }
    }

    @Test
    @DisplayName("null 로의 전이는 false (NPE 대신 false 로 흡수)")
    void canTransitionTo_nullIsFalse() {
        for (PaymentStatus s : PaymentStatus.values()) {
            assertThat(s.canTransitionTo(null)).isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("allStatuses")
    @DisplayName("isTerminal: FAILED/REFUNDED 만 true")
    void isTerminal(PaymentStatus s) {
        assertThat(s.isTerminal()).isEqualTo(TERMINAL.contains(s));
    }

    @Test
    @DisplayName("종착 상태에서 어떤 상태로도 전이 불가")
    void terminalStates_noOutgoing() {
        for (PaymentStatus terminal : TERMINAL) {
            for (PaymentStatus next : PaymentStatus.values()) {
                assertThat(terminal.canTransitionTo(next))
                        .as("%s -> %s should be illegal (terminal)", terminal, next)
                        .isFalse();
            }
        }
    }

    static Stream<PaymentStatus> allStatuses() {
        return Stream.of(PaymentStatus.values());
    }

    private record Pair(PaymentStatus from, PaymentStatus to) {
    }
}
