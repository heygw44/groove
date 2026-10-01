package com.groove.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderItemClaimStatusTest {

	@Nested
	@DisplayName("isInProgress()")
	class IsInProgress {

		@ParameterizedTest
		@EnumSource(value = OrderItemClaimStatus.class, names = {"CANCEL_REQUEST", "RETURN_REQUEST", "COLLECTING"})
		@DisplayName("CANCEL_REQUEST·RETURN_REQUEST·COLLECTING 이면 true 를 반환한다")
		void returnsTrueForInProgressStatuses(OrderItemClaimStatus status) {
			// when & then
			assertThat(OrderItemClaimStatus.isInProgress(status)).isTrue();
		}

		@ParameterizedTest
		@EnumSource(value = OrderItemClaimStatus.class,
				names = {"CANCEL_DONE", "CANCEL_REJECT", "RETURN_DONE", "RETURN_REJECT"})
		@DisplayName("종결된 클레임이면 false 를 반환한다")
		void returnsFalseForTerminalStatuses(OrderItemClaimStatus status) {
			// when & then
			assertThat(OrderItemClaimStatus.isInProgress(status)).isFalse();
		}

		@Test
		@DisplayName("null 이면 클레임이 없다는 뜻이라 false 를 반환한다")
		void returnsFalseForNull() {
			// when & then
			assertThat(OrderItemClaimStatus.isInProgress(null)).isFalse();
		}
	}
}
