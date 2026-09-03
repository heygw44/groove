package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;

class NoOpPaymentCancelHookTest {

	private final NoOpPaymentCancelHook hook = new NoOpPaymentCancelHook();

	@Nested
	@DisplayName("onPaidOrderCanceled()")
	class OnPaidOrderCanceled {

		@Test
		@DisplayName("로그만 남기고 예외 없이 반환한다")
		void logsWithoutThrowing() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), 1L);
			Order order = OrderFixture.withId(OrderFixture.create(member), 1L);

			// when & then
			assertThatCode(() -> hook.onPaidOrderCanceled(order)).doesNotThrowAnyException();
		}
	}
}
