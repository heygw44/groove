package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.cart.entity.Cart;
import com.groove.cart.entity.CartItem;
import com.groove.cart.repository.CartItemRepository;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.coupon.repository.MemberCouponRepository;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CartFixture;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.service.LimitedPurchaseWriter;
import com.groove.limited.service.LimitedRelease;
import com.groove.limited.service.LimitedReleaseSynchronizer;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderCreateResponse;
import com.groove.order.dto.OrderDetailResponse;
import com.groove.order.dto.OrderSearchRequest;
import com.groove.order.dto.OrderSummaryResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.mapper.OrderQueryMapper;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ADDRESS_ID = 10L;
	private static final Long PRODUCT_ID = 100L;
	private static final Long CART_ITEM_ID = 1000L;
	private static final Long MEMBER_COUPON_ID = 10000L;

	@Mock
	MemberRepository memberRepository;

	@Mock
	AddressRepository addressRepository;

	@Mock
	ProductRepository productRepository;

	@Mock
	LimitedDropRepository limitedDropRepository;

	@Mock
	LimitedPurchaseWriter limitedPurchaseWriter;

	@Mock
	LimitedReleaseSynchronizer limitedReleaseSynchronizer;

	@Mock
	CartItemRepository cartItemRepository;

	@Mock
	MemberCouponRepository memberCouponRepository;

	@Mock
	OrderStockService orderStockService;

	@Mock
	OrderRepository orderRepository;

	@Mock
	OrderQueryMapper orderQueryMapper;

	@Mock
	PaymentCancelHook paymentCancelHook;

	OrderService orderService;

	Member member;
	Artist artist;
	Product product;
	Address address;
	LocalDateTime now;

	@BeforeEach
	void setUp() {
		OrderNumberGenerator orderNumberGenerator = mock(OrderNumberGenerator.class);
		lenient().when(orderNumberGenerator.generate()).thenReturn("20260903-TESTAB12");
		lenient().when(orderRepository.save(any())).thenAnswer(invocation -> {
			Order savedOrder = invocation.getArgument(0);
			ReflectionTestUtils.setField(savedOrder, "id", 999L);
			return savedOrder;
		});
		Clock clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		orderService = new OrderService(memberRepository, addressRepository, productRepository, limitedDropRepository,
				limitedPurchaseWriter, limitedReleaseSynchronizer, cartItemRepository, memberCouponRepository,
				orderStockService, orderRepository, orderNumberGenerator, orderQueryMapper, paymentCancelHook, clock);

		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
		address = AddressFixture.withId(AddressFixture.create(member), ADDRESS_ID);
	}

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("장바구니 항목으로 주문하면 재고를 차감하고 장바구니 항목을 삭제한다")
		void createsOrderFromCartAndDeletesCartItems() {
			// given
			Cart cart = CartFixture.withId(CartFixture.createCart(member), 20L);
			CartItem cartItem = CartFixture.withId(CartFixture.createItem(cart, product, 2), CART_ITEM_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(cartItemRepository.findAllByIdInAndCartMemberId(List.of(CART_ITEM_ID), MEMBER_ID))
					.willReturn(List.of(cartItem));

			OrderCreateRequest request = new OrderCreateRequest(List.of(CART_ITEM_ID), null, null, ADDRESS_ID, null);

			// when
			OrderCreateResponse response = orderService.create(MEMBER_ID, request);

			// then
			BigDecimal expectedAmount = product.getPrice().multiply(BigDecimal.valueOf(2));
			assertThat(response.orderNumber()).isEqualTo("20260903-TESTAB12");
			assertThat(response.finalAmount()).isEqualByComparingTo(expectedAmount);
			verify(orderStockService).deduct(any());
			verify(cartItemRepository).deleteAll(List.of(cartItem));
		}

		@Test
		@DisplayName("단일 상품으로 주문하면 재고를 차감하고 주문을 생성한다")
		void createsOrderFromDirectProduct() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 3, ADDRESS_ID, null);

			// when
			OrderCreateResponse response = orderService.create(MEMBER_ID, request);

			// then
			BigDecimal expectedAmount = product.getPrice().multiply(BigDecimal.valueOf(3));
			assertThat(response.finalAmount()).isEqualByComparingTo(expectedAmount);
			verify(orderStockService).deduct(any());
			verify(cartItemRepository, never()).deleteAll(any());
		}

		@Test
		@DisplayName("재고 차감이 실패하면 예외가 그대로 전파된다")
		void propagatesExceptionWhenStockDeductionFails() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			willThrow(new BusinessException(ErrorCode.STOCK_INSUFFICIENT)).given(orderStockService).deduct(any());

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 100, ADDRESS_ID, null);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
			verify(orderRepository, never()).save(any());
		}

		@Test
		@DisplayName("숨김 상품을 주문하면 PRODUCT_HIDDEN 예외를 던진다")
		void throwsWhenProductHidden() {
			// given
			product.hide();
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, null);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_HIDDEN);
		}

		@Test
		@DisplayName("활성 드롭이 있는 상품을 주문하면 PRODUCT_LIMITED_ONLY 예외를 던진다")
		void throwsWhenProductHasActiveLimitedDrop() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(limitedDropRepository.existsByProductIdAndStatusNot(PRODUCT_ID, LimitedDropStatus.CLOSED))
					.willReturn(true);

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, null);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_LIMITED_ONLY);
		}

		@Test
		@DisplayName("타인 소유 배송지면 MEMBER_ADDRESS_NOT_FOUND 예외를 던진다")
		void throwsWhenAddressNotOwned() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.empty());

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, null);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_ADDRESS_NOT_FOUND);
		}

		@Test
		@DisplayName("존재하지 않거나 타인 소유인 장바구니 항목이 섞여 있으면 CART_ITEM_NOT_FOUND 예외를 던진다")
		void throwsWhenCartItemNotOwned() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(cartItemRepository.findAllByIdInAndCartMemberId(List.of(CART_ITEM_ID), MEMBER_ID))
					.willReturn(List.of());

			OrderCreateRequest request = new OrderCreateRequest(List.of(CART_ITEM_ID), null, null, ADDRESS_ID, null);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenMemberWithdrawn() {
			// given
			Member withdrawn = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, null);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
		}

		@Test
		@DisplayName("정액 쿠폰을 적용하면 할인 금액만큼 최종 금액이 줄고 쿠폰이 사용 처리된다")
		void appliesFixedCouponDiscount() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.withId(
					MemberCouponFixture.create(member, CouponFixture.fixed("FIXED5000", new BigDecimal("5000"))),
					MEMBER_COUPON_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(memberCouponRepository.findWithCouponByIdAndMemberIdForUpdate(MEMBER_COUPON_ID, MEMBER_ID))
					.willReturn(Optional.of(memberCoupon));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, MEMBER_COUPON_ID);

			// when
			OrderCreateResponse response = orderService.create(MEMBER_ID, request);

			// then
			assertThat(response.discountAmount()).isEqualByComparingTo(new BigDecimal("5000"));
			assertThat(response.finalAmount()).isEqualByComparingTo(product.getPrice().subtract(
					new BigDecimal("5000")));
			assertThat(memberCoupon.isUsed()).isTrue();
		}

		@Test
		@DisplayName("정률 쿠폰을 적용하면 최대 할인 한도가 적용된다")
		void appliesRateCouponDiscountWithCap() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.withId(
					MemberCouponFixture.create(member,
							CouponFixture.rate("RATE50", new BigDecimal("50"), new BigDecimal("10000"))),
					MEMBER_COUPON_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(memberCouponRepository.findWithCouponByIdAndMemberIdForUpdate(MEMBER_COUPON_ID, MEMBER_ID))
					.willReturn(Optional.of(memberCoupon));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, MEMBER_COUPON_ID);

			// when
			OrderCreateResponse response = orderService.create(MEMBER_ID, request);

			// then: 상품가 45000 의 50% 는 22500 이지만 한도 10000 이 적용된다
			assertThat(response.discountAmount()).isEqualByComparingTo(new BigDecimal("10000"));
		}

		@Test
		@DisplayName("만료된 쿠폰이면 COUPON_EXPIRED 예외를 던지고 재고와 주문을 저장하지 않는다")
		void throwsWhenCouponExpired() {
			// given
			MemberCoupon memberCoupon = MemberCouponFixture.withId(
					MemberCouponFixture.create(member,
							CouponFixture.expired(CouponFixture.fixed("EXPIRED", new BigDecimal("5000")))),
					MEMBER_COUPON_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(memberCouponRepository.findWithCouponByIdAndMemberIdForUpdate(MEMBER_COUPON_ID, MEMBER_ID))
					.willReturn(Optional.of(memberCoupon));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, MEMBER_COUPON_ID);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_EXPIRED);
			verify(orderStockService, never()).deduct(any());
			verify(orderRepository, never()).save(any());
		}

		@Test
		@DisplayName("이미 사용한 쿠폰이면 COUPON_ALREADY_USED 예외를 던진다")
		void throwsWhenCouponAlreadyUsed() {
			// given
			Coupon coupon = CouponFixture.fixed("USED5000", new BigDecimal("5000"));
			MemberCoupon memberCoupon = MemberCouponFixture.withId(
					MemberCouponFixture.used(MemberCouponFixture.create(member, coupon), 999L), MEMBER_COUPON_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(memberCouponRepository.findWithCouponByIdAndMemberIdForUpdate(MEMBER_COUPON_ID, MEMBER_ID))
					.willReturn(Optional.of(memberCoupon));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, MEMBER_COUPON_ID);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_ALREADY_USED);
			verify(orderStockService, never()).deduct(any());
		}

		@Test
		@DisplayName("본인 소유가 아닌 쿠폰이면 COUPON_NOT_FOUND 예외를 던진다")
		void throwsWhenCouponNotOwned() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(memberCouponRepository.findWithCouponByIdAndMemberIdForUpdate(MEMBER_COUPON_ID, MEMBER_ID))
					.willReturn(Optional.empty());

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, MEMBER_COUPON_ID);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_NOT_FOUND);
			verify(orderStockService, never()).deduct(any());
		}

		@Test
		@DisplayName("최소 주문 금액을 충족하지 못하면 COUPON_MIN_ORDER_AMOUNT_NOT_MET 예외를 던진다")
		void throwsWhenMinOrderAmountNotMet() {
			// given
			Coupon coupon = CouponFixture.withMinOrderAmount("MIN100000", DiscountType.FIXED,
					new BigDecimal("5000"), new BigDecimal("100000"));
			MemberCoupon memberCoupon = MemberCouponFixture.withId(
					MemberCouponFixture.create(member, coupon), MEMBER_COUPON_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(memberCouponRepository.findWithCouponByIdAndMemberIdForUpdate(MEMBER_COUPON_ID, MEMBER_ID))
					.willReturn(Optional.of(memberCoupon));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, MEMBER_COUPON_ID);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET);
			verify(orderStockService, never()).deduct(any());
		}
	}

	@Nested
	@DisplayName("getMyOrders()")
	class GetMyOrders {

		@Test
		@DisplayName("주문이 없으면 매퍼를 호출하지 않고 빈 페이지를 반환한다")
		void returnsEmptyPageWhenNoOrders() {
			// given
			given(orderQueryMapper.countMyOrders(any())).willReturn(0L);
			OrderSearchRequest request = new OrderSearchRequest(null, null, null);

			// when
			PageResponse<OrderSummaryResponse> response = orderService.getMyOrders(MEMBER_ID, request);

			// then
			assertThat(response.content()).isEmpty();
			assertThat(response.totalElements()).isZero();
			verify(orderQueryMapper, never()).findMyOrders(any());
		}

		@Test
		@DisplayName("주문이 있으면 목록과 총 개수를 반환한다")
		void returnsOrdersWhenPresent() {
			// given
			OrderSummaryResponse summary = new OrderSummaryResponse(1L, "20260903-TESTAB12", OrderStatus.PENDING,
					new BigDecimal("30000"), BigDecimal.ZERO, null, "Kind of Blue", 1, null, null);
			given(orderQueryMapper.countMyOrders(any())).willReturn(1L);
			given(orderQueryMapper.findMyOrders(any())).willReturn(List.of(summary));
			OrderSearchRequest request = new OrderSearchRequest(null, null, null);

			// when
			PageResponse<OrderSummaryResponse> response = orderService.getMyOrders(MEMBER_ID, request);

			// then
			assertThat(response.content()).containsExactly(summary);
			assertThat(response.totalElements()).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("getDetail()")
	class GetDetail {

		@Test
		@DisplayName("본인 주문이면 상세 정보를 반환한다")
		void returnsDetailForOwner() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 2), 600L);
			given(orderRepository.findWithItemsByIdAndMemberId(600L, MEMBER_ID)).willReturn(Optional.of(order));

			// when
			OrderDetailResponse response = orderService.getDetail(MEMBER_ID, 600L);

			// then
			assertThat(response.id()).isEqualTo(600L);
			assertThat(response.items()).hasSize(1);
		}

		@Test
		@DisplayName("타인 주문이거나 존재하지 않으면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsForOtherMemberOrder() {
			// given
			given(orderRepository.findWithItemsByIdAndMemberId(601L, MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> orderService.getDetail(MEMBER_ID, 601L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("PENDING 주문을 취소하면 재고를 복구하고 결제 취소 훅은 호출하지 않는다")
		void restoresStockAndSkipsHookWhenPending() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 2), 500L);
			given(orderRepository.findByIdForUpdate(500L)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(500L, MEMBER_ID)).willReturn(Optional.of(order));

			// when
			OrderDetailResponse response = orderService.cancel(MEMBER_ID, 500L, null);

			// then
			assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);
			verify(orderStockService).restore(order);
			verify(paymentCancelHook, never()).onPaidOrderCanceled(any());
		}

		@Test
		@DisplayName("PAID 주문을 취소하면 결제 취소 훅을 호출한다")
		void callsHookWhenPaid() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 501L);
			order.markPaid();
			given(orderRepository.findByIdForUpdate(501L)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(501L, MEMBER_ID)).willReturn(Optional.of(order));

			// when
			orderService.cancel(MEMBER_ID, 501L, new OrderCancelRequest("단순 변심"));

			// then
			verify(orderStockService).restore(order);
			verify(paymentCancelHook).onPaidOrderCanceled(order);
		}

		@Test
		@DisplayName("SHIPPED 주문을 취소하면 ORDER_CANNOT_CANCEL 예외를 던지고 재고를 건드리지 않는다")
		void throwsWhenShipped() {
			// given
			Order order = OrderFixture.withId(
					OrderFixture.markShipped(OrderFixture.createWithItem(member, product, 1)), 502L);
			given(orderRepository.findByIdForUpdate(502L)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(502L, MEMBER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> orderService.cancel(MEMBER_ID, 502L, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
			verify(orderStockService, never()).restore(any());
			verify(limitedPurchaseWriter, never()).revertByOrder(any(), any());
		}

		@Test
		@DisplayName("타인 주문이거나 존재하지 않으면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderNotFound() {
			// given
			given(orderRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> orderService.cancel(MEMBER_ID, 999L, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("쿠폰을 적용한 주문을 취소하면 쿠폰이 미사용 상태로 복구된다")
		void restoresCouponOnCancel() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 503L);
			MemberCoupon memberCoupon = MemberCouponFixture.create(member,
					CouponFixture.fixed("CANCEL5000", new BigDecimal("5000")));
			order.applyCoupon(memberCoupon, new BigDecimal("5000"));
			memberCoupon.use(order.getId());
			given(orderRepository.findByIdForUpdate(503L)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(503L, MEMBER_ID)).willReturn(Optional.of(order));

			// when
			orderService.cancel(MEMBER_ID, 503L, null);

			// then
			assertThat(memberCoupon.isUsed()).isFalse();
		}

		@Test
		@DisplayName("만료된 쿠폰을 적용한 주문도 취소하면 쿠폰이 복구된다")
		void restoresExpiredCouponOnCancel() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 504L);
			Coupon coupon = CouponFixture.fixed("CANCELEXPIRED", new BigDecimal("5000"));
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, coupon);
			order.applyCoupon(memberCoupon, new BigDecimal("5000"));
			memberCoupon.use(order.getId());
			CouponFixture.expired(coupon);
			given(orderRepository.findByIdForUpdate(504L)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(504L, MEMBER_ID)).willReturn(Optional.of(order));

			// when
			orderService.cancel(MEMBER_ID, 504L, null);

			// then
			assertThat(memberCoupon.isUsed()).isFalse();
		}

		@Test
		@DisplayName("한정반 주문을 취소하면 구매 이력을 되돌리고 커밋 후 Redis 선점을 해제한다")
		void revertsLimitedPurchaseAndReleasesAfterCommit() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 505L);
			given(orderRepository.findByIdForUpdate(505L)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(505L, MEMBER_ID)).willReturn(Optional.of(order));
			LimitedRelease release = new LimitedRelease(5L, MEMBER_ID);
			given(limitedPurchaseWriter.revertByOrder(505L, now)).willReturn(Optional.of(release));

			// when
			orderService.cancel(MEMBER_ID, 505L, null);

			// then
			verify(limitedReleaseSynchronizer).releaseAfterCommit(release);
		}

		@Test
		@DisplayName("한정반 주문이 아니면 Redis 선점 해제를 호출하지 않는다")
		void skipsLimitedReleaseForNormalOrder() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 506L);
			given(orderRepository.findByIdForUpdate(506L)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(506L, MEMBER_ID)).willReturn(Optional.of(order));
			given(limitedPurchaseWriter.revertByOrder(506L, now)).willReturn(Optional.empty());

			// when
			orderService.cancel(MEMBER_ID, 506L, null);

			// then
			verify(limitedReleaseSynchronizer, never()).releaseAfterCommit(any());
		}
	}
}
