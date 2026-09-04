package com.groove.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.cart.dto.CartItemResponse;
import com.groove.cart.dto.CartResponse;
import com.groove.cart.entity.Cart;
import com.groove.cart.entity.CartItem;
import com.groove.cart.repository.CartItemRepository;
import com.groove.cart.repository.CartRepository;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CartFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductImageRepository;
import com.groove.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long PRODUCT_ID = 100L;
	private static final Long CART_ID = 10L;
	private static final Long CART_ITEM_ID = 1000L;

	@Mock
	CartRepository cartRepository;

	@Mock
	CartItemRepository cartItemRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	ProductRepository productRepository;

	@Mock
	ProductImageRepository productImageRepository;

	@Mock
	StockRepository stockRepository;

	@Mock
	LimitedDropRepository limitedDropRepository;

	CartService cartService;

	Member member;
	Product product;
	Cart cart;

	@BeforeEach
	void setUp() {
		cartService = new CartService(cartRepository, cartItemRepository, memberRepository, productRepository,
				productImageRepository, stockRepository, limitedDropRepository);
		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
		cart = CartFixture.withId(CartFixture.createCart(member), CART_ID);
	}

	@Nested
	@DisplayName("getCart()")
	class GetCart {

		@Test
		@DisplayName("카트가 없으면 빈 응답을 반환한다")
		void returnsEmptyResponseWhenCartNotExists() {
			// given
			given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

			// when
			CartResponse response = cartService.getCart(MEMBER_ID);

			// then
			assertThat(response.cartId()).isNull();
			assertThat(response.items()).isEmpty();
			assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
		}

		@Test
		@DisplayName("담긴 상품들의 소계를 합산해 총액을 계산한다")
		void calculatesTotalAmount() {
			// given
			CartItem firstItem = CartFixture.withId(CartFixture.createItem(cart, product, 2), CART_ITEM_ID);
			Artist otherArtist = ArtistFixture.withId(2L);
			Product otherProduct = ProductFixture.withId(
					ProductFixture.create(otherArtist, "다른 상품", new BigDecimal("10000")), 200L);
			CartItem secondItem = CartFixture.withId(CartFixture.createItem(cart, otherProduct, 3), 1001L);

			given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
			given(cartItemRepository.findAllByCartIdOrderByIdAsc(CART_ID))
					.willReturn(List.of(firstItem, secondItem));
			given(productImageRepository.findAllByProductIdInAndSortOrder(any(), eq(0))).willReturn(List.of());
			given(stockRepository.findAllByProductIdIn(any())).willReturn(List.of());

			// when
			CartResponse response = cartService.getCart(MEMBER_ID);

			// then
			BigDecimal expected = product.getPrice().multiply(BigDecimal.valueOf(2))
					.add(otherProduct.getPrice().multiply(BigDecimal.valueOf(3)));
			assertThat(response.cartId()).isEqualTo(CART_ID);
			assertThat(response.items()).hasSize(2);
			assertThat(response.totalAmount()).isEqualByComparingTo(expected);
		}
	}

	@Nested
	@DisplayName("addItem()")
	class AddItem {

		@Test
		@DisplayName("동일 상품을 이미 담았으면 기존 항목 수량을 합산하고 새로 저장하지 않는다")
		void addsUpQuantityForExistingItem() {
			// given
			CartItem existing = CartFixture.withId(CartFixture.createItem(cart, product, 2), CART_ITEM_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
			given(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).willReturn(Optional.of(existing));
			given(stockRepository.findByProductId(PRODUCT_ID))
					.willReturn(Optional.of(StockFixture.create(product, 10)));
			given(productImageRepository.findAllByProductIdInAndSortOrder(any(), eq(0))).willReturn(List.of());

			// when
			CartItemResponse response = cartService.addItem(MEMBER_ID, CartFixture.addRequest(PRODUCT_ID, 3));

			// then
			assertThat(existing.getQuantity()).isEqualTo(5);
			assertThat(response.quantity()).isEqualTo(5);
			verify(cartItemRepository, never()).save(any());
		}

		@Test
		@DisplayName("처음 담는 상품이고 카트가 없으면 카트를 생성한다")
		void createsCartWhenFirstItem() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());
			given(cartRepository.save(any())).willReturn(cart);
			given(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).willReturn(Optional.empty());
			given(cartItemRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
			given(stockRepository.findByProductId(PRODUCT_ID))
					.willReturn(Optional.of(StockFixture.create(product, 10)));
			given(productImageRepository.findAllByProductIdInAndSortOrder(any(), eq(0))).willReturn(List.of());

			// when
			cartService.addItem(MEMBER_ID, CartFixture.addRequest(PRODUCT_ID, 1));

			// then
			verify(cartRepository).save(any());
		}

		@Test
		@DisplayName("숨김 상품이면 PRODUCT_HIDDEN 예외를 던진다")
		void throwsWhenProductHidden() {
			// given
			product.hide();
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			// when & then
			assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, CartFixture.addRequest(PRODUCT_ID, 1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_HIDDEN);
		}

		@Test
		@DisplayName("활성 드롭이 있는 상품이면 PRODUCT_LIMITED_ONLY 예외를 던진다")
		void throwsWhenProductHasActiveLimitedDrop() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(limitedDropRepository.existsByProductIdAndStatusNot(PRODUCT_ID, LimitedDropStatus.CLOSED))
					.willReturn(true);

			// when & then
			assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, CartFixture.addRequest(PRODUCT_ID, 1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_LIMITED_ONLY);
		}

		@Test
		@DisplayName("재고보다 많은 수량을 담으면 STOCK_INSUFFICIENT 예외를 던진다")
		void throwsWhenStockInsufficient() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));
			given(cartItemRepository.findByCartIdAndProductId(CART_ID, PRODUCT_ID)).willReturn(Optional.empty());
			given(cartItemRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
			given(stockRepository.findByProductId(PRODUCT_ID))
					.willReturn(Optional.of(StockFixture.create(product, 1)));

			// when & then
			assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, CartFixture.addRequest(PRODUCT_ID, 2)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenMemberWithdrawn() {
			// given
			Member withdrawn = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));

			// when & then
			assertThatThrownBy(() -> cartService.addItem(MEMBER_ID, CartFixture.addRequest(PRODUCT_ID, 1)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
		}
	}

	@Nested
	@DisplayName("updateQuantity()")
	class UpdateQuantity {

		@Test
		@DisplayName("본인 소유 항목이면 수량을 변경한다")
		void updatesQuantity() {
			// given
			CartItem item = CartFixture.withId(CartFixture.createItem(cart, product, 1), CART_ITEM_ID);
			given(cartItemRepository.findByIdAndCartMemberId(CART_ITEM_ID, MEMBER_ID)).willReturn(Optional.of(item));
			given(stockRepository.findByProductId(PRODUCT_ID))
					.willReturn(Optional.of(StockFixture.create(product, 10)));
			given(productImageRepository.findAllByProductIdInAndSortOrder(any(), eq(0))).willReturn(List.of());

			// when
			CartItemResponse response = cartService.updateQuantity(MEMBER_ID, CART_ITEM_ID,
					CartFixture.quantityRequest(5));

			// then
			assertThat(response.quantity()).isEqualTo(5);
		}

		@Test
		@DisplayName("타인 소유 항목이면 CART_ITEM_NOT_FOUND 예외를 던진다")
		void throwsWhenNotOwned() {
			// given
			given(cartItemRepository.findByIdAndCartMemberId(CART_ITEM_ID, MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> cartService.updateQuantity(MEMBER_ID, CART_ITEM_ID,
					CartFixture.quantityRequest(2)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("removeItem()")
	class RemoveItem {

		@Test
		@DisplayName("본인 소유 항목이면 삭제한다")
		void removesItem() {
			// given
			CartItem item = CartFixture.withId(CartFixture.createItem(cart, product, 1), CART_ITEM_ID);
			given(cartItemRepository.findByIdAndCartMemberId(CART_ITEM_ID, MEMBER_ID)).willReturn(Optional.of(item));

			// when
			cartService.removeItem(MEMBER_ID, CART_ITEM_ID);

			// then
			verify(cartItemRepository).delete(item);
		}

		@Test
		@DisplayName("타인 소유 항목이면 CART_ITEM_NOT_FOUND 예외를 던진다")
		void throwsWhenNotOwned() {
			// given
			given(cartItemRepository.findByIdAndCartMemberId(CART_ITEM_ID, MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> cartService.removeItem(MEMBER_ID, CART_ITEM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("clear()")
	class Clear {

		@Test
		@DisplayName("카트가 있으면 담긴 항목을 모두 삭제한다")
		void deletesAllItemsWhenCartExists() {
			// given
			given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(cart));

			// when
			cartService.clear(MEMBER_ID);

			// then
			verify(cartItemRepository).deleteAllByCartId(CART_ID);
		}

		@Test
		@DisplayName("카트가 없으면 아무 일도 일어나지 않는다")
		void doesNothingWhenCartNotExists() {
			// given
			given(cartRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

			// when
			cartService.clear(MEMBER_ID);

			// then
			verify(cartItemRepository, never()).deleteAllByCartId(any());
		}
	}
}
