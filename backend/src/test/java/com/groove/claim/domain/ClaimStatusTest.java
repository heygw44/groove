package com.groove.claim.domain;

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
 * ClaimStatus 전이 매트릭스 전수 검증 (6×6 = 36 케이스).
 *
 * <p>합법 전이는 #239 역물류 상태머신 기준 6종이며, 매트릭스의 나머지 셀(자기 전이 포함)은 모두 불법이다.
 * {@code OrderStatusTest}/{@code PaymentStatusTest} 와 동일한 SSOT 2단 구조를 따른다.
 */
@DisplayName("ClaimStatus — 전이 매트릭스 전수")
class ClaimStatusTest {

    private static final Set<Pair> LEGAL = Set.of(
            new Pair(ClaimStatus.REQUESTED, ClaimStatus.APPROVED),
            new Pair(ClaimStatus.REQUESTED, ClaimStatus.REJECTED),
            new Pair(ClaimStatus.APPROVED, ClaimStatus.IN_TRANSIT),
            new Pair(ClaimStatus.IN_TRANSIT, ClaimStatus.INSPECTING),
            new Pair(ClaimStatus.INSPECTING, ClaimStatus.REFUNDED),
            new Pair(ClaimStatus.INSPECTING, ClaimStatus.REJECTED)
    );

    private static final Set<ClaimStatus> TERMINAL = EnumSet.of(ClaimStatus.REFUNDED, ClaimStatus.REJECTED);

    static Stream<Arguments> allTransitions() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (ClaimStatus from : ClaimStatus.values()) {
            for (ClaimStatus to : ClaimStatus.values()) {
                b.add(Arguments.of(from, to));
            }
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allTransitions")
    @DisplayName("36 케이스 매트릭스: 합법 전이 표에 포함된 6종만 true, 나머지는 false")
    void canTransitionTo_matrix(ClaimStatus from, ClaimStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(LEGAL.contains(new Pair(from, to)));
    }

    @Test
    @DisplayName("자기 자신으로의 전이는 모두 불법")
    void canTransitionTo_selfIsAlwaysIllegal() {
        for (ClaimStatus s : ClaimStatus.values()) {
            assertThat(s.canTransitionTo(s)).as("self transition %s", s).isFalse();
        }
    }

    @Test
    @DisplayName("null 로의 전이는 false (NPE 대신 false 로 흡수)")
    void canTransitionTo_nullIsFalse() {
        for (ClaimStatus s : ClaimStatus.values()) {
            assertThat(s.canTransitionTo(null)).isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("allStatuses")
    @DisplayName("isTerminal: REFUNDED/REJECTED 만 true")
    void isTerminal(ClaimStatus s) {
        assertThat(s.isTerminal()).isEqualTo(TERMINAL.contains(s));
    }

    @Test
    @DisplayName("종착 상태에서 어떤 상태로도 전이 불가")
    void terminalStates_noOutgoing() {
        for (ClaimStatus terminal : TERMINAL) {
            for (ClaimStatus next : ClaimStatus.values()) {
                assertThat(terminal.canTransitionTo(next))
                        .as("%s -> %s should be illegal (terminal)", terminal, next)
                        .isFalse();
            }
        }
    }

    static Stream<ClaimStatus> allStatuses() {
        return Stream.of(ClaimStatus.values());
    }

    private record Pair(ClaimStatus from, ClaimStatus to) {
    }
}
