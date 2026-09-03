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
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderCreateResponse;
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
				stockRepository, stockHistoryRepository, orderRepository, orderNumberGenerator);

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
}
