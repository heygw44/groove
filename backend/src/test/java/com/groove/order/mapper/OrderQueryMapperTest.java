package com.groove.order.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.order.dto.AdminOrderItemSearchCondition;
import com.groove.order.dto.AdminOrderItemSummaryResponse;
import com.groove.order.dto.AdminOrderSearchCondition;
import com.groove.order.dto.AdminOrderSummaryResponse;
import com.groove.order.dto.OrderListItemRow;
import com.groove.order.dto.OrderSearchCondition;
import com.groove.order.dto.OrderSummaryResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.entity.OrderStatus;
import com.groove.order.entity.OrderStatusGroup;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.support.MybatisTestSupport;

import jakarta.persistence.EntityManager;

/** 공유 테스트 DB 에 다른 테스트가 남긴 주문이 섞이므로 자기 member id 로만 단언한다. */
class OrderQueryMapperTest extends MybatisTestSupport {

	@Autowired
	private OrderQueryMapper orderQueryMapper;

	@Autowired
	private EntityManager em;

	private Member owner;
	private Member other;
	private Artist artist;
	private Product kindOfBlue;
	private Product loveSupreme;

	@BeforeEach
	void setUp() {
		owner = MemberFixture.create("order-query-owner@groove.com");
		other = MemberFixture.create("order-query-other@groove.com");
		artist = ArtistFixture.create();
		em.persist(owner);
		em.persist(other);
		em.persist(artist);

		kindOfBlue = ProductFixture.create(artist, "OQM Kind of Blue");
		kindOfBlue.addImage("https://cdn.groove.com/kind-of-blue-0.jpg", 0);
		loveSupreme = ProductFixture.create(artist, "OQM A Love Supreme");
		em.persist(kindOfBlue.getAlbum());
		em.persist(loveSupreme.getAlbum());
		em.persist(kindOfBlue);
		em.persist(loveSupreme);
	}

	private Order persistOrder(Member member, String orderNumber, Product product, int quantity) {
		Order order = persistUnplacedOrder(member, orderNumber, product, quantity);
		order.place(LocalDateTime.now());
		em.flush();
		return order;
	}

	/** 결제 전(미확정) 주문을 만든다. placed_at 필터로 숨겨지는지 검증할 때 쓴다. */
	private Order persistUnplacedOrder(Member member, String orderNumber, Product product, int quantity) {
		Order order = OrderFixture.create(member, orderNumber);
		order.addItem(product, quantity);
		em.persist(order);
		em.flush();
		return order;
	}

	/** 상태가 서로 다른 상품주문 4개(PAID, SHIPPING, DELIVERED, CANCELED)를 가진 주문을 만든다. */
	private Order persistOrderWithMixedItemStatuses(String orderNumber) {
		Order order = OrderFixture.create(owner, orderNumber);
		order.addItem(kindOfBlue, 1);
		order.addItem(loveSupreme, 1);
		order.addItem(kindOfBlue, 1);
		order.addItem(loveSupreme, 1);
		order.place(LocalDateTime.now());
		List<OrderItemStatus> statuses = List.of(OrderItemStatus.PAID, OrderItemStatus.SHIPPING,
				OrderItemStatus.DELIVERED, OrderItemStatus.CANCELED);
		for (int i = 0; i < statuses.size(); i++) {
			ReflectionTestUtils.setField(order.getItems().get(i), "status", statuses.get(i));
		}
		em.persist(order);
		em.flush();
		em.clear();
		return order;
	}

	private static OrderSearchCondition condition(Long memberId, OrderStatusGroup statusGroup, int page, int size) {
		return new OrderSearchCondition(memberId, statusGroup, page, size);
	}

	private static AdminOrderSearchCondition adminCondition(OrderStatus status, String keyword,
			LocalDateTime fromAt, LocalDateTime toExclusiveAt) {
		return new AdminOrderSearchCondition(status, keyword, fromAt, toExclusiveAt, 0, 20);
	}

	@Nested
	@DisplayName("findMyOrders()")
	class FindMyOrders {

		@Test
		@DisplayName("다른 회원의 주문은 조회되지 않는다")
		void excludesOtherMemberOrders() {
			// given
			Order ownerOrder = persistOrder(owner, "20260903-OQM00001", kindOfBlue, 1);
			persistOrder(other, "20260903-OQM00002", loveSupreme, 1);
			em.clear();

			// when
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20));

			// then
			assertThat(result).extracting(OrderSummaryResponse::id).containsExactly(ownerOrder.getId());
		}

		@Test
		@DisplayName("statusGroup 으로 필터링하면 해당 그룹에 속한 상품주문이 있는 주문만 반환한다")
		void filtersByStatusGroup() {
			// given
			Order pendingOrder = persistOrder(owner, "20260903-OQM00003", kindOfBlue, 1);
			Order canceledOrder = persistOrder(owner, "20260903-OQM00004", loveSupreme, 1);
			canceledOrder.cancel("단순 변심");
			em.flush();
			em.clear();

			// when
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), OrderStatusGroup.CANCEL_RETURN, 0, 20));

			// then
			assertThat(result).extracting(OrderSummaryResponse::id).containsExactly(canceledOrder.getId());
			assertThat(result).extracting(OrderSummaryResponse::status).containsOnly(OrderStatus.CANCELED);
			assertThat(pendingOrder).isNotNull();
		}

		@Test
		@DisplayName("PAID 그룹으로 필터링하면 PAID 상태 상품주문이 없는 주문은 제외한다")
		void excludesOrdersWithoutMatchingStatusInGroup() {
			// given
			Order pendingOrder = persistOrder(owner, "20260903-OQM00029", kindOfBlue, 1);
			Order paidOrder = persistOrder(owner, "20260903-OQM00030", loveSupreme, 1);
			OrderFixture.markItemsStatus(paidOrder, OrderItemStatus.PAID);
			em.flush();
			em.clear();

			// when
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), OrderStatusGroup.PAID, 0, 20));

			// then
			assertThat(result).extracting(OrderSummaryResponse::id).containsExactly(paidOrder.getId());
			assertThat(pendingOrder).isNotNull();
		}

		@Test
		@DisplayName("CANCEL_RETURN 그룹은 종결 상태가 아니어도 진행 중인 클레임이 있으면 포함한다")
		void includesOrdersWithInProgressClaimInCancelReturnGroup() {
			// given
			Order preparingOrder = persistOrder(owner, "20260903-OQM00031", kindOfBlue, 1);
			OrderFixture.markItemsStatus(preparingOrder, OrderItemStatus.PREPARING);
			OrderFixture.markFirstItemClaimStatus(preparingOrder, OrderItemClaimStatus.CANCEL_REQUEST);
			Order paidOrder = persistOrder(owner, "20260903-OQM00032", loveSupreme, 1);
			OrderFixture.markItemsStatus(paidOrder, OrderItemStatus.PAID);
			em.flush();
			em.clear();

			// when
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), OrderStatusGroup.CANCEL_RETURN, 0, 20));

			// then
			assertThat(result).extracting(OrderSummaryResponse::id).containsExactly(preparingOrder.getId());
		}

		@Test
		@DisplayName("CANCEL_RETURN 그룹은 거부로 끝난 클레임이 걸린 주문을 제외한다")
		void excludesOrdersWithRejectedClaimInCancelReturnGroup() {
			// given
			Order cancelRejected = persistOrder(owner, "20260903-OQM00061", kindOfBlue, 1);
			OrderFixture.markItemsStatus(cancelRejected, OrderItemStatus.PREPARING);
			OrderFixture.markFirstItemClaimStatus(cancelRejected, OrderItemClaimStatus.CANCEL_REJECT);
			Order returnRejected = persistOrder(owner, "20260903-OQM00062", loveSupreme, 1);
			OrderFixture.markItemsStatus(returnRejected, OrderItemStatus.PURCHASE_CONFIRMED);
			OrderFixture.markFirstItemClaimStatus(returnRejected, OrderItemClaimStatus.RETURN_REJECT);
			em.flush();
			em.clear();

			// when
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), OrderStatusGroup.CANCEL_RETURN, 0, 20));

			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("최신순(placed_at DESC, id DESC)으로 반환한다")
		void sortsByLatest() {
			// given
			Order first = persistOrder(owner, "20260903-OQM00005", kindOfBlue, 1);
			Order second = persistOrder(owner, "20260903-OQM00006", kindOfBlue, 1);
			Order third = persistOrder(owner, "20260903-OQM00007", kindOfBlue, 1);
			em.clear();

			// when
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20));

			// then
			assertThat(result).extracting(OrderSummaryResponse::id)
					.containsExactly(third.getId(), second.getId(), first.getId());
		}

		@Test
		@DisplayName("생성 순서와 달라도 확정 시각(placed_at) 순으로 반환한다")
		void sortsByPlacedAtEvenWhenCreationOrderDiffers() {
			// given: created_at 순서는 first -> second 지만, 확정은 second 를 먼저 한다
			Order first = persistUnplacedOrder(owner, "20260903-OQM00024", kindOfBlue, 1);
			Order second = persistUnplacedOrder(owner, "20260903-OQM00025", kindOfBlue, 1);
			LocalDateTime baseTime = LocalDateTime.now();
			second.place(baseTime);
			first.place(baseTime.plusMinutes(1));
			em.flush();
			em.clear();

			// when
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20));

			// then
			assertThat(result).extracting(OrderSummaryResponse::id)
					.containsExactly(first.getId(), second.getId());
		}

		@Test
		@DisplayName("결제 전이라 확정되지 않은 주문은 조회되지 않는다")
		void excludesUnplacedOrders() {
			// given
			Order placed = persistOrder(owner, "20260903-OQM00026", kindOfBlue, 1);
			persistUnplacedOrder(owner, "20260903-OQM00027", kindOfBlue, 1);
			em.clear();

			// when
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20));

			// then
			assertThat(result).extracting(OrderSummaryResponse::id).containsExactly(placed.getId());
		}

		@Test
		@DisplayName("size·page 로 페이징하면 offset 이후 항목만 반환한다")
		void paginatesWithOffset() {
			// given: 최신순 정렬이므로 가장 먼저 저장한 주문이 두 번째 페이지로 밀려난다
			Order oldest = persistOrder(owner, "20260903-OQM00008", kindOfBlue, 1);
			persistOrder(owner, "20260903-OQM00009", kindOfBlue, 1);
			persistOrder(owner, "20260903-OQM00010", kindOfBlue, 1);
			em.clear();

			// when
			List<OrderSummaryResponse> secondPage = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 1, 2));

			// then
			assertThat(secondPage).extracting(OrderSummaryResponse::id).containsExactly(oldest.getId());
		}

		@Test
		@DisplayName("대표 상품명과 상품 수를 함께 반환한다")
		void returnsRepresentativeProductNameAndItemCount() {
			// given
			Order order = OrderFixture.create(owner, "20260903-OQM00011");
			order.addItem(kindOfBlue, 1);
			order.addItem(loveSupreme, 2);
			order.place(LocalDateTime.now());
			em.persist(order);
			em.flush();
			em.clear();

			// when
			OrderSummaryResponse result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20)).stream()
					.filter(summary -> summary.id().equals(order.getId()))
					.findFirst()
					.orElseThrow();

			// then
			assertThat(result.representativeProductName()).isEqualTo("OQM Kind of Blue");
			assertThat(result.itemCount()).isEqualTo(2);
		}

		@Test
		@DisplayName("대표 상품에 sort_order 0 이미지가 있으면 썸네일 URL 을 반환한다")
		void returnsThumbnailWhenRepresentativeProductHasImage() {
			// given
			Order order = persistOrder(owner, "20260903-OQM00012", kindOfBlue, 1);
			em.clear();

			// when
			OrderSummaryResponse result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20)).stream()
					.filter(summary -> summary.id().equals(order.getId()))
					.findFirst()
					.orElseThrow();

			// then
			assertThat(result.thumbnailUrl()).isEqualTo("https://cdn.groove.com/kind-of-blue-0.jpg");
		}

		@Test
		@DisplayName("대표 상품에 이미지가 없으면 썸네일 URL 이 null 이다")
		void returnsNullThumbnailWhenNoImage() {
			// given
			Order order = persistOrder(owner, "20260903-OQM00013", loveSupreme, 1);
			em.clear();

			// when
			OrderSummaryResponse result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20)).stream()
					.filter(summary -> summary.id().equals(order.getId()))
					.findFirst()
					.orElseThrow();

			// then
			assertThat(result.thumbnailUrl()).isNull();
		}

		@Test
		@DisplayName("쿠폰을 적용한 주문은 할인 금액과 쿠폰명을 함께 반환한다")
		void returnsDiscountAmountAndCouponNameWhenCouponApplied() {
			// given
			Coupon coupon = CouponFixture.fixed("OQM-COUPON", new BigDecimal("5000"));
			em.persist(coupon);
			MemberCoupon memberCoupon = MemberCouponFixture.create(owner, coupon);
			em.persist(memberCoupon);
			Order order = OrderFixture.create(owner, "20260903-OQM00016");
			order.addItem(kindOfBlue, 1);
			order.applyCoupon(memberCoupon, new BigDecimal("5000"));
			order.place(LocalDateTime.now());
			em.persist(order);
			em.flush();
			em.clear();

			// when
			OrderSummaryResponse result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20)).stream()
					.filter(summary -> summary.id().equals(order.getId()))
					.findFirst()
					.orElseThrow();

			// then
			assertThat(result.discountAmount()).isEqualByComparingTo(new BigDecimal("5000"));
			assertThat(result.couponName()).isEqualTo("테스트 쿠폰");
		}

		@Test
		@DisplayName("쿠폰이 없는 주문은 할인 금액 0 과 쿠폰명 null 을 반환한다")
		void returnsZeroDiscountAndNullCouponNameWhenNoCoupon() {
			// given
			Order order = persistOrder(owner, "20260903-OQM00017", kindOfBlue, 1);
			em.clear();

			// when
			OrderSummaryResponse result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20)).stream()
					.filter(summary -> summary.id().equals(order.getId()))
					.findFirst()
					.orElseThrow();

			// then
			assertThat(result.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(result.couponName()).isNull();
		}
	}

	@Nested
	@DisplayName("countMyOrders()")
	class CountMyOrders {

		@Test
		@DisplayName("동일 조건의 findMyOrders 결과 개수와 일치한다")
		void matchesFindResultSize() {
			// given
			persistOrder(owner, "20260903-OQM00014", kindOfBlue, 1);
			persistOrder(owner, "20260903-OQM00015", kindOfBlue, 1);
			em.clear();

			// when
			long count = orderQueryMapper.countMyOrders(condition(owner.getId(), null, 0, 20));
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20));

			// then
			assertThat(count).isEqualTo(result.size());
		}

		@Test
		@DisplayName("확정되지 않은 주문은 개수에 포함하지 않는다")
		void excludesUnplacedOrders() {
			// given
			persistUnplacedOrder(owner, "20260903-OQM00028", kindOfBlue, 1);
			em.clear();

			// when
			long count = orderQueryMapper.countMyOrders(condition(owner.getId(), null, 0, 20));

			// then
			assertThat(count).isZero();
		}

		@Test
		@DisplayName("statusGroup 으로 필터링하면 findMyOrders 와 같은 개수를 반환한다")
		void matchesFindResultSizeWithStatusGroup() {
			// given
			Order paidOrder = persistOrder(owner, "20260903-OQM00033", kindOfBlue, 1);
			OrderFixture.markItemsStatus(paidOrder, OrderItemStatus.PAID);
			persistOrder(owner, "20260903-OQM00034", loveSupreme, 1);
			em.flush();
			em.clear();

			// when
			long count = orderQueryMapper.countMyOrders(condition(owner.getId(), OrderStatusGroup.PAID, 0, 20));
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), OrderStatusGroup.PAID, 0, 20));

			// then
			assertThat(count).isEqualTo(1L);
			assertThat(count).isEqualTo(result.size());
		}
	}

	@Nested
	@DisplayName("findItemsByOrderIds()")
	class FindItemsByOrderIds {

		@Test
		@DisplayName("요청한 주문 id 에 속한 상품 행만 반환한다")
		void returnsOnlyRowsForRequestedOrderIds() {
			// given
			Order target = OrderFixture.create(owner, "20260903-OQM00018");
			target.addItem(kindOfBlue, 1);
			target.addItem(loveSupreme, 2);
			em.persist(target);
			Order other = persistOrder(owner, "20260903-OQM00019", kindOfBlue, 1);
			em.flush();
			em.clear();

			// when
			List<OrderListItemRow> result = orderQueryMapper.findItemsByOrderIds(List.of(target.getId()), null);

			// then
			assertThat(result).hasSize(2);
			assertThat(result).extracting(OrderListItemRow::orderId).containsOnly(target.getId());
			assertThat(other).isNotNull();
		}

		@Test
		@DisplayName("한 주문의 상품 행은 order_item id 오름차순(담긴 순서)으로 반환한다")
		void returnsRowsOrderedByItemId() {
			// given
			Order order = OrderFixture.create(owner, "20260903-OQM00020");
			order.addItem(kindOfBlue, 1);
			order.addItem(loveSupreme, 2);
			em.persist(order);
			em.flush();
			em.clear();

			// when
			List<OrderListItemRow> result = orderQueryMapper.findItemsByOrderIds(List.of(order.getId()), null);

			// then
			assertThat(result).extracting(OrderListItemRow::productName)
					.containsExactly("OQM Kind of Blue", "OQM A Love Supreme");
		}

		@Test
		@DisplayName("상품에 sort_order 0 이미지가 있으면 썸네일 URL 을 반환한다")
		void returnsThumbnailForProductWithImage() {
			// given
			Order order = persistOrder(owner, "20260903-OQM00021", kindOfBlue, 1);
			em.clear();

			// when
			List<OrderListItemRow> result = orderQueryMapper.findItemsByOrderIds(List.of(order.getId()), null);

			// then
			assertThat(result).extracting(OrderListItemRow::thumbnailUrl)
					.containsExactly("https://cdn.groove.com/kind-of-blue-0.jpg");
		}

		@Test
		@DisplayName("상품에 이미지가 없으면 썸네일 URL 이 null 이다")
		void returnsNullThumbnailForProductWithoutImage() {
			// given
			Order order = persistOrder(owner, "20260903-OQM00022", loveSupreme, 1);
			em.clear();

			// when
			List<OrderListItemRow> result = orderQueryMapper.findItemsByOrderIds(List.of(order.getId()), null);

			// then
			assertThat(result).extracting(OrderListItemRow::thumbnailUrl).containsExactly((String) null);
		}

		@Test
		@DisplayName("수량과 상품 금액(단가 x 수량)을 함께 반환한다")
		void returnsQuantityAndLineAmount() {
			// given
			Order order = persistOrder(owner, "20260903-OQM00023", kindOfBlue, 3);
			em.clear();

			// when
			OrderListItemRow result = orderQueryMapper.findItemsByOrderIds(List.of(order.getId()), null).get(0);

			// then
			assertThat(result.quantity()).isEqualTo(3);
			assertThat(result.lineAmount())
					.isEqualByComparingTo(kindOfBlue.getPrice().multiply(BigDecimal.valueOf(3)));
		}

		@Test
		@DisplayName("상품주문번호·상태·배송 정보를 함께 반환한다")
		void returnsProductOrderNumberStatusAndShippingInfo() {
			// given
			Order order = persistOrder(owner, "20260903-OQM00035", kindOfBlue, 1);
			OrderFixture.markItemsStatus(order, OrderItemStatus.SHIPPING);
			em.flush();
			em.clear();

			// when
			OrderListItemRow result = orderQueryMapper.findItemsByOrderIds(List.of(order.getId()), null).get(0);

			// then
			assertThat(result.productOrderNumber()).isEqualTo("20260903-OQM00035-01");
			assertThat(result.status()).isEqualTo(OrderItemStatus.SHIPPING);
			assertThat(result.claimStatus()).isNull();
		}

		@Test
		@DisplayName("할인이 없으면 결제 금액은 상품 금액과 같다")
		void returnsLineAmountAsPaidAmountWhenNoDiscount() {
			// given
			Order order = persistOrder(owner, "20260903-OQM00036", kindOfBlue, 2);
			em.clear();

			// when
			OrderListItemRow result = orderQueryMapper.findItemsByOrderIds(List.of(order.getId()), null).get(0);

			// then
			assertThat(result.paidAmount()).isEqualByComparingTo(result.lineAmount());
		}

		@Test
		@DisplayName("할인 배분(discount_share)만큼 결제 금액이 상품 금액보다 작다")
		void returnsLineAmountMinusDiscountShareAsPaidAmount() {
			// given
			Coupon coupon = CouponFixture.fixed("OQM-PAIDAMT", new BigDecimal("5000"));
			em.persist(coupon);
			MemberCoupon memberCoupon = MemberCouponFixture.create(owner, coupon);
			em.persist(memberCoupon);
			Order order = OrderFixture.create(owner, "20260903-OQM00037");
			order.addItem(kindOfBlue, 1);
			order.applyCoupon(memberCoupon, new BigDecimal("5000"));
			order.place(LocalDateTime.now());
			em.persist(order);
			em.flush();
			em.clear();

			// when
			OrderListItemRow result = orderQueryMapper.findItemsByOrderIds(List.of(order.getId()), null).get(0);

			// then
			assertThat(result.paidAmount())
					.isEqualByComparingTo(result.lineAmount().subtract(new BigDecimal("5000")));
		}

		@Test
		@DisplayName("statusGroup 이 없으면 주문의 모든 상품 행을 반환한다")
		void returnsAllItemsWhenStatusGroupIsNull() {
			// given
			Order order = persistOrderWithMixedItemStatuses("20260903-OQM00040");

			// when
			List<OrderListItemRow> result = orderQueryMapper.findItemsByOrderIds(List.of(order.getId()), null);

			// then
			assertThat(result).extracting(OrderListItemRow::status).containsExactly(OrderItemStatus.PAID,
					OrderItemStatus.SHIPPING, OrderItemStatus.DELIVERED, OrderItemStatus.CANCELED);
		}

		@Test
		@DisplayName("statusGroup 이 SHIPPING 이면 배송중 상품 행만 반환한다")
		void returnsOnlyShippingItemsForShippingGroup() {
			// given
			Order order = persistOrderWithMixedItemStatuses("20260903-OQM00041");

			// when
			List<OrderListItemRow> result = orderQueryMapper.findItemsByOrderIds(List.of(order.getId()),
					OrderStatusGroup.SHIPPING);

			// then
			assertThat(result).extracting(OrderListItemRow::status).containsExactly(OrderItemStatus.SHIPPING);
		}

		@Test
		@DisplayName("statusGroup 이 CANCEL_RETURN 이면 취소 상태 행과 진행 중인 클레임이 걸린 행을 함께 반환한다")
		void returnsCanceledAndClaimedItemsForCancelReturnGroup() {
			// given
			Order order = OrderFixture.create(owner, "20260903-OQM00042");
			order.addItem(kindOfBlue, 1);
			order.addItem(loveSupreme, 1);
			order.addItem(kindOfBlue, 1);
			order.place(LocalDateTime.now());
			ReflectionTestUtils.setField(order.getItems().get(0), "status", OrderItemStatus.CANCELED);
			ReflectionTestUtils.setField(order.getItems().get(1), "status", OrderItemStatus.DELIVERED);
			ReflectionTestUtils.setField(order.getItems().get(1), "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			ReflectionTestUtils.setField(order.getItems().get(2), "status", OrderItemStatus.DELIVERED);
			em.persist(order);
			em.flush();
			em.clear();

			// when
			List<OrderListItemRow> result = orderQueryMapper.findItemsByOrderIds(List.of(order.getId()),
					OrderStatusGroup.CANCEL_RETURN);

			// then
			assertThat(result).extracting(OrderListItemRow::productOrderNumber)
					.containsExactly("20260903-OQM00042-01", "20260903-OQM00042-02");
		}

		@Test
		@DisplayName("statusGroup 이 CANCEL_RETURN 이면 거부로 끝난 클레임 행은 제외한다")
		void excludesRejectedClaimItemsForCancelReturnGroup() {
			// given
			Order order = OrderFixture.create(owner, "20260903-OQM00063");
			order.addItem(kindOfBlue, 1);
			order.addItem(loveSupreme, 1);
			order.addItem(kindOfBlue, 1);
			order.place(LocalDateTime.now());
			ReflectionTestUtils.setField(order.getItems().get(0), "status", OrderItemStatus.CANCELED);
			ReflectionTestUtils.setField(order.getItems().get(1), "status", OrderItemStatus.DELIVERED);
			ReflectionTestUtils.setField(order.getItems().get(1), "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			ReflectionTestUtils.setField(order.getItems().get(2), "status", OrderItemStatus.DELIVERED);
			ReflectionTestUtils.setField(order.getItems().get(2), "claimStatus", OrderItemClaimStatus.RETURN_REJECT);
			em.persist(order);
			em.flush();
			em.clear();

			// when
			List<OrderListItemRow> result = orderQueryMapper.findItemsByOrderIds(List.of(order.getId()),
					OrderStatusGroup.CANCEL_RETURN);

			// then
			assertThat(result).extracting(OrderListItemRow::productOrderNumber)
					.containsExactly("20260903-OQM00063-01", "20260903-OQM00063-02");
		}
	}

	@Nested
	@DisplayName("findAdminOrders()")
	class FindAdminOrders {

		@Test
		@DisplayName("status 로 필터링하면 해당 상태의 주문만 반환한다")
		void filtersByStatus() {
			// given
			Order paidOrder = persistOrder(owner, "20260903-OQMADM001", kindOfBlue, 1);
			paidOrder.markPaid();
			Order pendingOrder = persistOrder(owner, "20260903-OQMADM002", kindOfBlue, 1);
			em.flush();
			em.clear();

			// when
			List<AdminOrderSummaryResponse> result = orderQueryMapper.findAdminOrders(
					adminCondition(OrderStatus.PAID, null, null, null));

			// then
			assertThat(result).extracting(AdminOrderSummaryResponse::id).contains(paidOrder.getId());
			assertThat(result).extracting(AdminOrderSummaryResponse::id).doesNotContain(pendingOrder.getId());
		}

		@Test
		@DisplayName("keyword 로 회원 이메일을 검색하면 해당 회원의 주문만 반환한다")
		void filtersByMemberEmailKeyword() {
			// given
			Order ownerOrder = persistOrder(owner, "20260903-OQMADM003", kindOfBlue, 1);
			Order otherOrder = persistOrder(other, "20260903-OQMADM004", kindOfBlue, 1);
			em.clear();

			// when
			List<AdminOrderSummaryResponse> result = orderQueryMapper.findAdminOrders(
					adminCondition(null, "order-query-owner", null, null));

			// then
			assertThat(result).extracting(AdminOrderSummaryResponse::id).contains(ownerOrder.getId());
			assertThat(result).extracting(AdminOrderSummaryResponse::id).doesNotContain(otherOrder.getId());
		}

		@Test
		@DisplayName("keyword 로 주문번호를 검색하면 해당 주문만 반환한다")
		void filtersByOrderNumberKeyword() {
			// given
			Order target = persistOrder(owner, "20260903-OQMADM005", kindOfBlue, 1);
			Order another = persistOrder(owner, "20260903-OQMADM006", kindOfBlue, 1);
			em.clear();

			// when
			List<AdminOrderSummaryResponse> result = orderQueryMapper.findAdminOrders(
					adminCondition(null, "OQMADM005", null, null));

			// then
			assertThat(result).extracting(AdminOrderSummaryResponse::id).contains(target.getId());
			assertThat(result).extracting(AdminOrderSummaryResponse::id).doesNotContain(another.getId());
		}

		@Test
		@DisplayName("생성일 범위로 필터링하면 경계값을 포함해 반환한다")
		void filtersByCreatedAtRangeInclusiveOfBoundaries() {
			// given
			Order order = persistOrder(owner, "20260903-OQMADM007", kindOfBlue, 1);
			em.clear();
			LocalDateTime createdAt = em.find(Order.class, order.getId()).getCreatedAt();

			// when
			List<AdminOrderSummaryResponse> inRange = orderQueryMapper.findAdminOrders(
					adminCondition(null, null, createdAt, createdAt.plusSeconds(1)));
			List<AdminOrderSummaryResponse> beforeRange = orderQueryMapper.findAdminOrders(
					adminCondition(null, null, createdAt.plusSeconds(1), null));

			// then
			assertThat(inRange).extracting(AdminOrderSummaryResponse::id).contains(order.getId());
			assertThat(beforeRange).extracting(AdminOrderSummaryResponse::id).doesNotContain(order.getId());
		}

		@Test
		@DisplayName("필터가 없으면 전체 주문 중 내가 생성한 주문이 포함된다")
		void includesOwnOrdersWhenNoFilter() {
			// given
			Order order = persistOrder(owner, "20260903-OQMADM008", kindOfBlue, 1);
			em.clear();

			// when
			List<AdminOrderSummaryResponse> result = orderQueryMapper.findAdminOrders(
					adminCondition(null, null, null, null));

			// then
			assertThat(result).extracting(AdminOrderSummaryResponse::id).contains(order.getId());
		}

		@Test
		@DisplayName("결제 전이라 확정되지 않은 주문은 조회되지 않는다")
		void excludesUnplacedOrders() {
			// given
			Order unplaced = persistUnplacedOrder(owner, "20260903-OQMADM010", kindOfBlue, 1);
			em.clear();

			// when
			List<AdminOrderSummaryResponse> result = orderQueryMapper.findAdminOrders(
					adminCondition(null, null, null, null));

			// then
			assertThat(result).extracting(AdminOrderSummaryResponse::id).doesNotContain(unplaced.getId());
		}
	}

	@Nested
	@DisplayName("countAdminOrders()")
	class CountAdminOrders {

		@Test
		@DisplayName("동일 조건의 findAdminOrders 결과 개수 이상이다")
		void matchesFindResultSize() {
			// given
			persistOrder(owner, "20260903-OQMADM009", kindOfBlue, 1);
			em.clear();

			// when
			long count = orderQueryMapper.countAdminOrders(adminCondition(null, "order-query-owner", null, null));
			List<AdminOrderSummaryResponse> result = orderQueryMapper.findAdminOrders(
					adminCondition(null, "order-query-owner", null, null));

			// then
			assertThat(count).isEqualTo(result.size());
		}
	}

	@Nested
	@DisplayName("findAdminOrderItems()")
	class FindAdminOrderItems {

		@Test
		@DisplayName("가상계좌 결제 주문의 상품주문만 virtualAccountPayment 가 true 다")
		void flagsVirtualAccountPaymentOnly() {
			// given
			Order virtualAccountOrder = persistOrder(owner, "20260903-OQMVA0001", kindOfBlue, 1);
			Order cardOrder = persistOrder(other, "20260903-OQMVA0002", loveSupreme, 1);
			em.persist(PaymentFixture.virtualAccountApproved(virtualAccountOrder, "toss-va-oqm-1"));
			em.persist(PaymentFixture.approved(cardOrder, "toss-card-oqm-1"));
			em.flush();
			em.clear();

			// when: 회원 이메일 접두어로 두 주문만 좁힌다
			List<AdminOrderItemSummaryResponse> result = orderQueryMapper.findAdminOrderItems(
					new AdminOrderItemSearchCondition(null, "order-query-", null, null, 0, 100));

			// then
			assertThat(result).filteredOn(row -> row.orderId().equals(virtualAccountOrder.getId()))
					.hasSize(1)
					.allMatch(AdminOrderItemSummaryResponse::virtualAccountPayment);
			assertThat(result).filteredOn(row -> row.orderId().equals(cardOrder.getId()))
					.hasSize(1)
					.noneMatch(AdminOrderItemSummaryResponse::virtualAccountPayment);
		}

		@Test
		@DisplayName("결제 행이 없는 주문은 virtualAccountPayment 가 false 다")
		void flagsFalseWhenNoPayment() {
			// given
			Order order = persistOrder(owner, "20260903-OQMVA0003", kindOfBlue, 1);
			em.clear();

			// when
			List<AdminOrderItemSummaryResponse> result = orderQueryMapper.findAdminOrderItems(
					new AdminOrderItemSearchCondition(null, "order-query-", null, null, 0, 100));

			// then
			assertThat(result).filteredOn(row -> row.orderId().equals(order.getId()))
					.hasSize(1)
					.noneMatch(AdminOrderItemSummaryResponse::virtualAccountPayment);
		}

		@Test
		@DisplayName("CANCEL_RETURN 그룹은 거부로 끝난 클레임 행을 제외하고 진행 중 행은 포함한다")
		void excludesRejectedClaimItemsForCancelReturnGroup() {
			// given
			Order rejected = persistOrder(owner, "20260903-OQMVA0004", kindOfBlue, 1);
			OrderFixture.markItemsStatus(rejected, OrderItemStatus.PREPARING);
			OrderFixture.markFirstItemClaimStatus(rejected, OrderItemClaimStatus.CANCEL_REJECT);
			Order requested = persistOrder(owner, "20260903-OQMVA0005", loveSupreme, 1);
			OrderFixture.markItemsStatus(requested, OrderItemStatus.PREPARING);
			OrderFixture.markFirstItemClaimStatus(requested, OrderItemClaimStatus.CANCEL_REQUEST);
			em.flush();
			em.clear();

			// when
			List<AdminOrderItemSummaryResponse> result = orderQueryMapper.findAdminOrderItems(
					new AdminOrderItemSearchCondition(OrderStatusGroup.CANCEL_RETURN, "order-query-", null, null,
							0, 100));

			// then
			assertThat(result).extracting(AdminOrderItemSummaryResponse::orderId)
					.contains(requested.getId())
					.doesNotContain(rejected.getId());
		}
	}

	@Nested
	@DisplayName("countAdminOrderItems()")
	class CountAdminOrderItems {

		@Test
		@DisplayName("keyword 로 회원 이메일을 검색하면 같은 조건의 findAdminOrderItems 결과 개수와 같다")
		void matchesFindResultSizeWithKeyword() {
			// given
			persistOrderWithMixedItemStatuses("20260903-OQMCNT001");
			persistUnplacedOrder(owner, "20260903-OQMCNT002", kindOfBlue, 1);
			em.clear();
			AdminOrderItemSearchCondition condition =
					new AdminOrderItemSearchCondition(null, "order-query-owner", null, null, 0, 100);

			// when
			long count = orderQueryMapper.countAdminOrderItems(condition);
			List<AdminOrderItemSummaryResponse> result = orderQueryMapper.findAdminOrderItems(condition);

			// then
			assertThat(count).isEqualTo(result.size());
		}

		@Test
		@DisplayName("keyword 가 없으면 회원 조인 없이 결제 확정 주문의 상품주문만 센다")
		void countsPlacedItemsWithoutKeyword() {
			// given: 공유 DB 에 다른 테스트 데이터가 있을 수 있어 증가분으로 단언한다. 조건을 달리해 세션 캐시를 피한다.
			LocalDateTime from = LocalDateTime.now().minusDays(1);
			long before = orderQueryMapper.countAdminOrderItems(
					new AdminOrderItemSearchCondition(null, null, from, from.plusDays(10), 0, 20));
			persistOrderWithMixedItemStatuses("20260903-OQMCNT003");
			persistUnplacedOrder(owner, "20260903-OQMCNT004", kindOfBlue, 1);
			em.clear();

			// when
			long after = orderQueryMapper.countAdminOrderItems(
					new AdminOrderItemSearchCondition(null, null, from, from.plusDays(11), 0, 20));

			// then
			assertThat(after - before).isEqualTo(4);
		}
	}
}
