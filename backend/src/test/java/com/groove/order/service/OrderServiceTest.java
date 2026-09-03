package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.cart.entity.Cart;
import com.groove.cart.entity.CartItem;
import com.groove.cart.repository.CartItemRepository;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CartFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
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

	@Mock
	MemberRepository memberRepository;

	@Mock
	AddressRepository addressRepository;

	@Mock
	ProductRepository productRepository;

	@Mock
	CartItemRepository cartItemRepository;

	@Mock
	StockRepository stockRepository;

	@Mock
	StockHistoryRepository stockHistoryRepository;

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
	Stock stock;

	@BeforeEach
	void setUp() {
		OrderNumberGenerator orderNumberGenerator = mock(OrderNumberGenerator.class);
		lenient().when(orderNumberGenerator.generate()).thenReturn("20260903-TESTAB12");
		orderService = new OrderService(memberRepository, addressRepository, productRepository, cartItemRepository,
				stockRepository, stockHistoryRepository, orderRepository, orderNumberGenerator, orderQueryMapper,
				paymentCancelHook);

		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
		address = AddressFixture.withId(AddressFixture.create(member), ADDRESS_ID);
		stock = StockFixture.create(product, 10);
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
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));

			OrderCreateRequest request = new OrderCreateRequest(List.of(CART_ITEM_ID), null, null, ADDRESS_ID, null);

			// when
			OrderCreateResponse response = orderService.create(MEMBER_ID, request);

			// then
			BigDecimal expectedAmount = product.getPrice().multiply(BigDecimal.valueOf(2));
			assertThat(response.orderNumber()).isEqualTo("20260903-TESTAB12");
			assertThat(response.finalAmount()).isEqualByComparingTo(expectedAmount);
			assertThat(stock.getQuantity()).isEqualTo(8);
			verify(cartItemRepository).deleteAll(List.of(cartItem));
		}

		@Test
		@DisplayName("단일 상품으로 주문하면 재고를 차감하고 주문을 생성한다")
		void createsOrderFromDirectProduct() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 3, ADDRESS_ID, null);

			// when
			OrderCreateResponse response = orderService.create(MEMBER_ID, request);

			// then
			BigDecimal expectedAmount = product.getPrice().multiply(BigDecimal.valueOf(3));
			assertThat(response.finalAmount()).isEqualByComparingTo(expectedAmount);
			assertThat(stock.getQuantity()).isEqualTo(7);
			verify(cartItemRepository, never()).deleteAll(any());
		}

		@Test
		@DisplayName("재고보다 많은 수량을 주문하면 STOCK_INSUFFICIENT 예외를 던진다")
		void throwsWhenStockInsufficient() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 100, ADDRESS_ID, null);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
		}

		@Test
		@DisplayName("품절(재고 0)인 상품을 주문하면 STOCK_INSUFFICIENT 예외를 던진다")
		void throwsWhenSoldOut() {
			// given
			Stock soldOutStock = StockFixture.create(product, 0);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(soldOutStock));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, null);

			// when & then
			assertThatThrownBy(() -> orderService.create(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
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
		@DisplayName("재고 UPDATE 를 flush 한 뒤에 이력을 저장한다")
		void flushesStockBeforeSavingHistory() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(addressRepository.findByIdAndMemberId(ADDRESS_ID, MEMBER_ID)).willReturn(Optional.of(address));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));

			OrderCreateRequest request = new OrderCreateRequest(null, PRODUCT_ID, 1, ADDRESS_ID, null);

			// when
			orderService.create(MEMBER_ID, request);

			// then
			InOrder order = inOrder(stockRepository, stockHistoryRepository);
			order.verify(stockRepository).flush();
			order.verify(stockHistoryRepository).save(any());
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
					new BigDecimal("30000"), "Kind of Blue", 1, null, null);
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
		@DisplayName("PENDING 주문을 취소하면 재고를 복구하고 CANCEL 이력을 남기며 결제 취소 훅은 호출하지 않는다")
		void restoresStockAndSkipsHookWhenPending() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 2), 500L);
			given(orderRepository.findWithItemsByIdAndMemberId(500L, MEMBER_ID)).willReturn(Optional.of(order));
			Stock stock = StockFixture.create(product, 8);
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));

			// when
			OrderDetailResponse response = orderService.cancel(MEMBER_ID, 500L, null);

			// then
			assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);
			assertThat(stock.getQuantity()).isEqualTo(10);
			verify(stockHistoryRepository).save(any());
			verify(paymentCancelHook, never()).onPaidOrderCanceled(any());
		}

		@Test
		@DisplayName("PAID 주문을 취소하면 결제 취소 훅을 호출한다")
		void callsHookWhenPaid() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 501L);
			order.markPaid();
			given(orderRepository.findWithItemsByIdAndMemberId(501L, MEMBER_ID)).willReturn(Optional.of(order));
			Stock stock = StockFixture.create(product, 9);
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));

			// when
			orderService.cancel(MEMBER_ID, 501L, new OrderCancelRequest("단순 변심"));

			// then
			verify(paymentCancelHook).onPaidOrderCanceled(order);
		}

		@Test
		@DisplayName("SHIPPED 주문을 취소하면 ORDER_CANNOT_CANCEL 예외를 던지고 재고를 건드리지 않는다")
		void throwsWhenShipped() {
			// given
			Order order = OrderFixture.withId(
					OrderFixture.markShipped(OrderFixture.createWithItem(member, product, 1)), 502L);
			given(orderRepository.findWithItemsByIdAndMemberId(502L, MEMBER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> orderService.cancel(MEMBER_ID, 502L, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
			verify(stockRepository, never()).findAllWithProductByProductIdInForUpdate(any());
		}

		@Test
		@DisplayName("타인 주문이거나 존재하지 않으면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderNotFound() {
			// given
			given(orderRepository.findWithItemsByIdAndMemberId(999L, MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> orderService.cancel(MEMBER_ID, 999L, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("재고 UPDATE 를 flush 한 뒤에 CANCEL 이력을 저장한다")
		void flushesStockBeforeSavingHistory() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 503L);
			given(orderRepository.findWithItemsByIdAndMemberId(503L, MEMBER_ID)).willReturn(Optional.of(order));
			Stock stock = StockFixture.create(product, 9);
			given(stockRepository.findAllWithProductByProductIdInForUpdate(List.of(PRODUCT_ID)))
					.willReturn(List.of(stock));

			// when
			orderService.cancel(MEMBER_ID, 503L, null);

			// then
			InOrder inOrder = inOrder(stockRepository, stockHistoryRepository);
			inOrder.verify(stockRepository).flush();
			inOrder.verify(stockHistoryRepository).save(any());
		}
	}
}
