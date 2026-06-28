package com.groove.coupon.domain;

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
 * CouponStatus 상태 전이 매트릭스 전수 검증 (3×3 = 9 케이스). 합법 전이는 4종(ACTIVE↔SUSPENDED, 둘 다 →ENDED)
 * 이며 나머지 셀은 모두 불법이다.
 */
@DisplayName("CouponStatus — 전이 매트릭스 전수")
class CouponStatusTest {

    private static final Set<Pair> LEGAL = Set.of(
            new Pair(CouponStatus.ACTIVE, CouponStatus.SUSPENDED),
            new Pair(CouponStatus.ACTIVE, CouponStatus.ENDED),
            new Pair(CouponStatus.SUSPENDED, CouponStatus.ACTIVE),
            new Pair(CouponStatus.SUSPENDED, CouponStatus.ENDED)
    );

    private static final Set<CouponStatus> TERMINAL = EnumSet.of(CouponStatus.ENDED);

    static Stream<Arguments> allTransitions() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (CouponStatus from : CouponStatus.values()) {
            for (CouponStatus to : CouponStatus.values()) {
                b.add(Arguments.of(from, to));
            }
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allTransitions")
    @DisplayName("9 케이스 매트릭스: 합법 전이 표에 포함된 4종만 true, 나머지는 false")
    void canTransitionTo_matrix(CouponStatus from, CouponStatus to) {
        boolean expected = LEGAL.contains(new Pair(from, to));

        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    @DisplayName("자기 자신으로의 전이는 모두 불법")
    void canTransitionTo_selfIsAlwaysIllegal() {
        for (CouponStatus s : CouponStatus.values()) {
            assertThat(s.canTransitionTo(s))
                    .as("self transition %s -> %s", s, s)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("null 로의 전이는 false (NPE 대신 false 로 흡수)")
    void canTransitionTo_nullIsFalse() {
        for (CouponStatus s : CouponStatus.values()) {
            assertThat(s.canTransitionTo(null)).isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("allStatuses")
    @DisplayName("isTerminal: ENDED 만 true")
    void isTerminal(CouponStatus s) {
        assertThat(s.isTerminal()).isEqualTo(TERMINAL.contains(s));
    }

    static Stream<CouponStatus> allStatuses() {
        return Stream.of(CouponStatus.values());
    }

    @Test
    @DisplayName("종착 상태(ENDED)에서 어떤 상태로도 전이 불가")
    void terminalStates_noOutgoing() {
        for (CouponStatus terminal : TERMINAL) {
            for (CouponStatus next : CouponStatus.values()) {
                assertThat(terminal.canTransitionTo(next))
                        .as("%s -> %s should be illegal (terminal)", terminal, next)
                        .isFalse();
            }
        }
    }

    private record Pair(CouponStatus from, CouponStatus to) {
    }
}
