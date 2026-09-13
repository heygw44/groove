package com.groove.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderStatusTest {

	@Nested
	@DisplayName("canTransitionTo()")
	class CanTransitionTo {

		@ParameterizedTest
		@CsvSource({
			"PAID, PREPARING",
			"PAID, CANCELED",
			"PREPARING, SHIPPED",
			"PREPARING, CANCELED",
			"SHIPPED, DELIVERED"
		})
		@DisplayName("관리자 허용 전이면 true 를 반환한다")
		void returnsTrueForAllowedTransitions(OrderStatus from, OrderStatus to) {
			// when & then
			assertThat(from.canTransitionTo(to)).isTrue();
		}

		@ParameterizedTest
		@CsvSource({
			"PENDING, PREPARING",
			"SHIPPED, CANCELED",
			"DELIVERED, SHIPPED",
			"CANCELED, PAID",
			"PAID, DELIVERED",
			"PAID, PAID"
		})
		@DisplayName("허용되지 않은 전이면 false 를 반환한다")
		void returnsFalseForDisallowedTransitions(OrderStatus from, OrderStatus to) {
			// when & then
			assertThat(from.canTransitionTo(to)).isFalse();
		}
	}
}
