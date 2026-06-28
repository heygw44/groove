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
 * MemberCouponStatus 상태 전이 매트릭스 전수 검증 (4×4 = 16 케이스). 합법 전이는 5종:
 * ISSUED→{USED,EXPIRED,CANCELLED}, USED→{ISSUED,EXPIRED}. EXPIRED·CANCELLED 는 종착이다.
 */
@DisplayName("MemberCouponStatus — 전이 매트릭스 전수")
class MemberCouponStatusTest {

    private static final Set<Pair> LEGAL = Set.of(
            new Pair(MemberCouponStatus.ISSUED, MemberCouponStatus.USED),
            new Pair(MemberCouponStatus.ISSUED, MemberCouponStatus.EXPIRED),
            new Pair(MemberCouponStatus.ISSUED, MemberCouponStatus.CANCELLED),
            new Pair(MemberCouponStatus.USED, MemberCouponStatus.ISSUED),
            new Pair(MemberCouponStatus.USED, MemberCouponStatus.EXPIRED)
    );

    private static final Set<MemberCouponStatus> TERMINAL =
            EnumSet.of(MemberCouponStatus.EXPIRED, MemberCouponStatus.CANCELLED);

    static Stream<Arguments> allTransitions() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (MemberCouponStatus from : MemberCouponStatus.values()) {
            for (MemberCouponStatus to : MemberCouponStatus.values()) {
                b.add(Arguments.of(from, to));
            }
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allTransitions")
    @DisplayName("16 케이스 매트릭스: 합법 전이 표에 포함된 5종만 true, 나머지는 false")
    void canTransitionTo_matrix(MemberCouponStatus from, MemberCouponStatus to) {
        boolean expected = LEGAL.contains(new Pair(from, to));

        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    @DisplayName("자기 자신으로의 전이는 모두 불법")
    void canTransitionTo_selfIsAlwaysIllegal() {
        for (MemberCouponStatus s : MemberCouponStatus.values()) {
            assertThat(s.canTransitionTo(s))
                    .as("self transition %s -> %s", s, s)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("null 로의 전이는 false (NPE 대신 false 로 흡수)")
    void canTransitionTo_nullIsFalse() {
        for (MemberCouponStatus s : MemberCouponStatus.values()) {
            assertThat(s.canTransitionTo(null)).isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("allStatuses")
    @DisplayName("isTerminal: EXPIRED/CANCELLED 만 true")
    void isTerminal(MemberCouponStatus s) {
        assertThat(s.isTerminal()).isEqualTo(TERMINAL.contains(s));
    }

    static Stream<MemberCouponStatus> allStatuses() {
        return Stream.of(MemberCouponStatus.values());
    }

    @Test
    @DisplayName("종착 상태(EXPIRED/CANCELLED)에서 어떤 상태로도 전이 불가")
    void terminalStates_noOutgoing() {
        for (MemberCouponStatus terminal : TERMINAL) {
            for (MemberCouponStatus next : MemberCouponStatus.values()) {
                assertThat(terminal.canTransitionTo(next))
                        .as("%s -> %s should be illegal (terminal)", terminal, next)
                        .isFalse();
            }
        }
    }

    /** 만료 배치 네이티브 SQL 의 'ISSUED'/'EXPIRED' 리터럴과 enum 이름이 정합한지 검증한다. */
    @Test
    @DisplayName("ISSUED/EXPIRED 이름 가정 보호 — 만료 배치 네이티브 SQL 의 리터럴과 정합")
    void enumNames_pinnedForExpirationQuery() {
        assertThat(MemberCouponStatus.ISSUED.name()).isEqualTo("ISSUED");
        assertThat(MemberCouponStatus.EXPIRED.name()).isEqualTo("EXPIRED");
    }

    private record Pair(MemberCouponStatus from, MemberCouponStatus to) {
    }
}
