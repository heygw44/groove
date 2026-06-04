package com.groove.shipping.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShippingStatus — 상태 전이 규칙")
class ShippingStatusTest {

    @Test
    @DisplayName("PREPARING → SHIPPED 만 허용")
    void preparing_transitions() {
        assertThat(ShippingStatus.PREPARING.canTransitionTo(ShippingStatus.SHIPPED)).isTrue();
        assertThat(ShippingStatus.PREPARING.canTransitionTo(ShippingStatus.DELIVERED)).isFalse();
        assertThat(ShippingStatus.PREPARING.canTransitionTo(ShippingStatus.PREPARING)).isFalse();
        assertThat(ShippingStatus.PREPARING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("SHIPPED → DELIVERED 만 허용")
    void shipped_transitions() {
        assertThat(ShippingStatus.SHIPPED.canTransitionTo(ShippingStatus.DELIVERED)).isTrue();
        assertThat(ShippingStatus.SHIPPED.canTransitionTo(ShippingStatus.PREPARING)).isFalse();
        assertThat(ShippingStatus.SHIPPED.canTransitionTo(ShippingStatus.SHIPPED)).isFalse();
        assertThat(ShippingStatus.SHIPPED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("DELIVERED 는 종착 — 어떤 전이도 거부")
    void delivered_isTerminal() {
        assertThat(ShippingStatus.DELIVERED.isTerminal()).isTrue();
        for (ShippingStatus next : ShippingStatus.values()) {
            assertThat(ShippingStatus.DELIVERED.canTransitionTo(next)).isFalse();
        }
    }

    @Test
    @DisplayName("null 로의 전이는 항상 불법")
    void nullNext_rejected() {
        for (ShippingStatus from : ShippingStatus.values()) {
            assertThat(from.canTransitionTo(null)).isFalse();
        }
    }
}
