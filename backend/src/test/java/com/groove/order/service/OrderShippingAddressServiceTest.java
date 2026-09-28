package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.order.dto.OrderDetailResponse;
import com.groove.order.entity.OrderStatus;

@ExtendWith(MockitoExtension.class)
class OrderShippingAddressServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ORDER_ID = 500L;
	private static final Long ADDRESS_ID = 78L;

	@Mock
	private OrderShippingAddressWriter writer;

	@Mock
	private OrderService orderService;

	@InjectMocks
	private OrderShippingAddressService orderShippingAddressService;

	@Test
	@DisplayName("배송지를 바꾼 뒤 액션 이후 상세 응답을 반환한다")
	void changesAddressThenReturnsDetailAfterAction() {
		// given
		OrderDetailResponse response = new OrderDetailResponse(ORDER_ID, "20260903-TESTAB12", OrderStatus.PENDING,
				new BigDecimal("90000"), BigDecimal.ZERO, new BigDecimal("90000"), null, List.of(), null, null,
				null, null, null, null, null);
		given(orderService.getDetailAfterAction(MEMBER_ID, ORDER_ID)).willReturn(response);

		// when
		OrderDetailResponse result = orderShippingAddressService.changeShippingAddress(MEMBER_ID, ORDER_ID,
				ADDRESS_ID);

		// then
		assertThat(result).isEqualTo(response);
		verify(writer).changeAddress(MEMBER_ID, ORDER_ID, ADDRESS_ID);
		verify(orderService).getDetailAfterAction(MEMBER_ID, ORDER_ID);
	}
}
