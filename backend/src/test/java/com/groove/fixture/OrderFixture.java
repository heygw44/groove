package com.groove.fixture;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.member.entity.Member;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.entity.OrderSource;
import com.groove.order.entity.OrderStatus;
import com.groove.order.entity.ShippingAddress;
import com.groove.product.entity.Product;

public final class OrderFixture {

	private static final String ORDER_NUMBER = "20260903-TESTAB12";
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private OrderFixture() {
	}

	public static ShippingAddress shippingAddress() {
		return ShippingAddress.of("김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호");
	}

	public static Order create(Member member) {
		return create(member, ORDER_NUMBER);
	}

	public static Order create(Member member, String orderNumber) {
		return Order.create(orderNumber, member, shippingAddress(), OrderSource.CART, LocalDateTime.now());
	}

	public static Order create(Member member, String orderNumber, OrderSource orderSource) {
		return Order.create(orderNumber, member, shippingAddress(), orderSource, LocalDateTime.now());
	}

	public static Order createWithItem(Member member, Product product, int quantity) {
		Order order = create(member);
		order.addItem(product, quantity);
		return order;
	}

	public static Order createWithItems(Member member, List<Product> products) {
		Order order = create(member, "20260903-CP" + SEQUENCE.incrementAndGet());
		products.forEach(product -> order.addItem(product, 1));
		return order;
	}

	public static Order place(Order order) {
		order.place(LocalDateTime.now());
		return order;
	}

	public static Order withId(Order order, Long id) {
		ReflectionTestUtils.setField(order, "id", id);
		return order;
	}

	/**
	 * 주문 상태는 결제 생애주기(PAID)에 두고 담긴 상품주문을 모두 배송완료(DELIVERED)로 옮긴다. 배송 진행은 상품주문
	 * 단위(item.status)로만 표현되므로 주문 상태는 PAID 로 남는다.
	 */
	public static Order markDelivered(Order order) {
		markPaid(order);
		return markItemsStatus(order, OrderItemStatus.DELIVERED);
	}

	public static Order markPaid(Order order) {
		ReflectionTestUtils.setField(order, "status", OrderStatus.PAID);
		return order;
	}

	/**
	 * 담긴 상품주문의 상태(item.status)만 리플렉션으로 바꾼다. order.status 만 바꾸는 markPaid() 는 상품주문 상태까지
	 * 옮기지 않으므로, "팔렸다" 판정이 item.status 기준인 조회(추천·판매량·리뷰 자격 등)를 검증하는 테스트는 이
	 * 메서드로 항목의 상태를 함께 맞춘다.
	 */
	public static Order markItemsStatus(Order order, OrderItemStatus status) {
		order.getItems().forEach(item -> ReflectionTestUtils.setField(item, "status", status));
		return order;
	}

	/** 첫 번째 상품주문에만 진행 중이거나 끝난 클레임 상태를 심는다. 클레임 워크플로 API 가 아직 없어 직접 설정한다. */
	public static Order markFirstItemClaimStatus(Order order, OrderItemClaimStatus claimStatus) {
		ReflectionTestUtils.setField(order.getItems().get(0), "claimStatus", claimStatus);
		return order;
	}

	/** 첫 번째 상품주문의 배송완료 시각을 직접 심는다. 반품 기한(D7) 경계 테스트에 쓴다. */
	public static Order markFirstItemDeliveredAt(Order order, LocalDateTime deliveredAt) {
		ReflectionTestUtils.setField(order.getItems().get(0), "deliveredAt", deliveredAt);
		return order;
	}

	/** 첫 번째 상품주문의 발송처리 시각을 직접 심는다. 자동 배송완료 스케줄러 경계 테스트에 쓴다. */
	public static Order markFirstItemShippedAt(Order order, LocalDateTime shippedAt) {
		ReflectionTestUtils.setField(order.getItems().get(0), "shippedAt", shippedAt);
		return order;
	}

	public static Order withExpiresAt(Order order, LocalDateTime expiresAt) {
		ReflectionTestUtils.setField(order, "expiresAt", expiresAt);
		return order;
	}

	public static OrderCreateRequest cartRequest(List<Long> cartItemIds, Long addressId) {
		return new OrderCreateRequest(cartItemIds, null, null, addressId, null);
	}

	public static OrderCreateRequest directRequest(Long productId, int quantity, Long addressId) {
		return new OrderCreateRequest(null, productId, quantity, addressId, null);
	}

	public static OrderCreateRequest directRequestWithCoupon(Long productId, int quantity, Long addressId,
			Long memberCouponId) {
		return new OrderCreateRequest(null, productId, quantity, addressId, memberCouponId);
	}
}
