package com.groove.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.cart.entity.Cart;
import com.groove.cart.entity.CartItem;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CartFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.DataJpaTestSupport;

class CartItemRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private CartRepository cartRepository;

	@Autowired
	private CartItemRepository cartItemRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 카트에 같은 상품을 두 번 담으면 유니크 제약 위반이 발생한다")
		void throwsWhenCartAndProductDuplicated() {
			// given
			Member member = memberRepository.save(MemberFixture.create("cart-item-uk@groove.com"));
			Cart cart = cartRepository.save(CartFixture.createCart(member));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			cartItemRepository.saveAndFlush(CartFixture.createItem(cart, product, 1));

			// when & then
			assertThatThrownBy(() -> cartItemRepository.saveAndFlush(CartFixture.createItem(cart, product, 2)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Nested
	@DisplayName("findByIdAndCartMemberId()")
	class FindByIdAndCartMemberId {

		@Test
		@DisplayName("다른 회원의 카트에 담긴 항목이면 empty 를 반환한다")
		void returnsEmptyForOtherMember() {
			// given
			Member owner = memberRepository.save(MemberFixture.create("cart-item-owner@groove.com"));
			Member other = memberRepository.save(MemberFixture.create("cart-item-other@groove.com"));
			Cart ownerCart = cartRepository.save(CartFixture.createCart(owner));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			CartItem item = cartItemRepository.save(CartFixture.createItem(ownerCart, product, 1));

			// when
			Optional<CartItem> found = cartItemRepository.findByIdAndCartMemberId(item.getId(), other.getId());

			// then
			assertThat(found).isEmpty();
		}

		@Test
		@DisplayName("본인 카트에 담긴 항목이면 반환한다")
		void returnsItemForOwner() {
			// given
			Member owner = memberRepository.save(MemberFixture.create("cart-item-owner2@groove.com"));
			Cart ownerCart = cartRepository.save(CartFixture.createCart(owner));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			CartItem item = cartItemRepository.save(CartFixture.createItem(ownerCart, product, 1));

			// when
			Optional<CartItem> found = cartItemRepository.findByIdAndCartMemberId(item.getId(), owner.getId());

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getId()).isEqualTo(item.getId());
		}
	}

	@Nested
	@DisplayName("deleteAllByCartId()")
	class DeleteAllByCartId {

		@Test
		@DisplayName("삭제 후에는 해당 카트의 항목 목록이 비어 있다")
		void removesAllItemsOfCart() {
			// given
			Member member = memberRepository.save(MemberFixture.create("cart-item-clear@groove.com"));
			Cart cart = cartRepository.save(CartFixture.createCart(member));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product firstProduct = productRepository.save(ProductFixture.create(artist, "상품1"));
			Product secondProduct = productRepository.save(ProductFixture.create(artist, "상품2"));
			cartItemRepository.save(CartFixture.createItem(cart, firstProduct, 1));
			cartItemRepository.save(CartFixture.createItem(cart, secondProduct, 1));

			// when
			cartItemRepository.deleteAllByCartId(cart.getId());

			// then
			List<CartItem> remaining = cartItemRepository.findAllByCartIdOrderByIdAsc(cart.getId());
			assertThat(remaining).isEmpty();
		}
	}
}
