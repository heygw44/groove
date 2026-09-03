package com.groove.fixture;

import java.util.List;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.member.entity.Member;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.entity.ShippingAddress;
import com.groove.product.entity.Product;

public final class OrderFixture {

	private static final String ORDER_NUMBER = "20260903-TESTAB12";

	private OrderFixture() {
	}

	public static ShippingAddress shippingAddress() {
		return ShippingAddress.of("김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호");
	}

	public static Order create(Member member) {
		return create(member, ORDER_NUMBER);
	}

	public static Order create(Member member, String orderNumber) {
		return Order.create(orderNumber, member, shippingAddress());
	}

	public static Order createWithItem(Member member, Product product, int quantity) {
		Order order = create(member);
		order.addItem(product, quantity);
		return order;
	}

	public static Order withId(Order order, Long id) {
		ReflectionTestUtils.setField(order, "id", id);
		return order;
	}

	public static Order markShipped(Order order) {
		ReflectionTestUtils.setField(order, "status", OrderStatus.SHIPPED);
		return order;
	}

	public static OrderCreateRequest cartRequest(List<Long> cartItemIds, Long addressId) {
		return new OrderCreateRequest(cartItemIds, null, null, addressId, null);
	}

	public static OrderCreateRequest directRequest(Long productId, int quantity, Long addressId) {
		return new OrderCreateRequest(null, productId, quantity, addressId, null);
	}
}
