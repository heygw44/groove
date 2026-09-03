package com.groove.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderStatusTest {

	@Nested
	@DisplayName("isCancelable()")
	class IsCancelable {

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, names = {"PENDING", "PAID"})
		@DisplayName("PENDING 또는 PAID 면 true 를 반환한다")
		void returnsTrueForPendingOrPaid(OrderStatus status) {
			// when & then
			assertThat(status.isCancelable()).isTrue();
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, names = {"PREPARING", "SHIPPED", "DELIVERED", "CANCELED", "REFUNDED"})
		@DisplayName("그 외 상태면 false 를 반환한다")
		void returnsFalseForOtherStatuses(OrderStatus status) {
			// when & then
			assertThat(status.isCancelable()).isFalse();
		}
	}
}
