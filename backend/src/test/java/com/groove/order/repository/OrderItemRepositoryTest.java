package com.groove.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItemStatus;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.DataJpaTestSupport;

class OrderItemRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private OrderItemRepository orderItemRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Nested
	@DisplayName("existsByOrderMemberIdAndProductIdAndStatusIn()")
	class ExistsByOrderMemberIdAndProductIdAndStatusIn {

		@Test
		@DisplayName("DELIVERED 상품주문이면 true 를 반환한다")
		void returnsTrueWhenDelivered() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-delivered@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-delivered"));
			Product createdProduct = ProductFixture.create(artist);
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);
			Order order = OrderFixture.createWithItem(member, product, 1);
			OrderFixture.markDelivered(order);
			OrderFixture.markItemsStatus(order, OrderItemStatus.DELIVERED);
			orderRepository.saveAndFlush(order);

			// when
			boolean exists = orderItemRepository.existsByOrderMemberIdAndProductIdAndStatusIn(member.getId(),
					product.getId(), OrderItemStatus.REVIEWABLE);

			// then
			assertThat(exists).isTrue();
		}

		@Test
		@DisplayName("PAID 상태면 false 를 반환한다")
		void returnsFalseWhenPaid() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-paid@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-paid"));
			Product createdProduct = ProductFixture.create(artist);
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);
			Order order = OrderFixture.createWithItem(member, product, 1);
			order.markPaid();
			orderRepository.saveAndFlush(order);

			// when
			boolean exists = orderItemRepository.existsByOrderMemberIdAndProductIdAndStatusIn(member.getId(),
					product.getId(), OrderItemStatus.REVIEWABLE);

			// then
			assertThat(exists).isFalse();
		}

		@Test
		@DisplayName("다른 상품이면 false 를 반환한다")
		void returnsFalseForOtherProduct() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-other@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-other"));
			Product deliveredProduct = ProductFixture.create(artist);
			albumRepository.save(deliveredProduct.getAlbum());
			deliveredProduct = productRepository.save(deliveredProduct);
			Product otherProduct = ProductFixture.create(artist, "다른 상품");
			albumRepository.save(otherProduct.getAlbum());
			otherProduct = productRepository.save(otherProduct);
			Order order = OrderFixture.createWithItem(member, deliveredProduct, 1);
			OrderFixture.markDelivered(order);
			OrderFixture.markItemsStatus(order, OrderItemStatus.DELIVERED);
			orderRepository.saveAndFlush(order);

			// when
			boolean exists = orderItemRepository.existsByOrderMemberIdAndProductIdAndStatusIn(member.getId(),
					otherProduct.getId(), OrderItemStatus.REVIEWABLE);

			// then
			assertThat(exists).isFalse();
		}
	}

	@Nested
	@DisplayName("findProductIdsByMemberIdAndStatusIn()")
	class FindProductIdsByMemberIdAndStatusIn {

		@Test
		@DisplayName("PAID·DELIVERED 상품주문에 담긴 상품 id 를 반환한다")
		void returnsProductIdsForSoldItems() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-status@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-status"));
			Product paidProduct = ProductFixture.create(artist, "결제완료 상품");
			albumRepository.save(paidProduct.getAlbum());
			paidProduct = productRepository.save(paidProduct);
			Product deliveredProduct = ProductFixture.create(artist, "배송완료 상품");
			albumRepository.save(deliveredProduct.getAlbum());
			deliveredProduct = productRepository.save(deliveredProduct);
			Product pendingProduct = ProductFixture.create(artist, "결제대기 상품");
			albumRepository.save(pendingProduct.getAlbum());
			pendingProduct = productRepository.save(pendingProduct);

			Order paidOrder = OrderFixture.createWithItems(member, List.of(paidProduct));
			paidOrder.markPaid();
			orderRepository.saveAndFlush(paidOrder);
			Order deliveredOrder = OrderFixture.createWithItems(member, List.of(deliveredProduct));
			OrderFixture.markDelivered(deliveredOrder);
			OrderFixture.markItemsStatus(deliveredOrder, OrderItemStatus.DELIVERED);
			orderRepository.saveAndFlush(deliveredOrder);
			orderRepository.saveAndFlush(OrderFixture.createWithItems(member, List.of(pendingProduct)));

			// when
			List<Long> result = orderItemRepository.findProductIdsByMemberIdAndStatusIn(member.getId(),
					OrderItemStatus.SOLD);

			// then
			assertThat(result).containsExactlyInAnyOrder(paidProduct.getId(), deliveredProduct.getId());
		}

		@Test
		@DisplayName("같은 상품이 담긴 주문이 두 개면 중복 없이 한 번만 반환한다")
		void distinctsSameProductAcrossMultipleOrders() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-distinct@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-distinct"));
			Product createdProduct = ProductFixture.create(artist);
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);

			Order paidOrder = OrderFixture.createWithItems(member, List.of(product));
			paidOrder.markPaid();
			orderRepository.saveAndFlush(paidOrder);
			Order deliveredOrder = OrderFixture.createWithItems(member, List.of(product));
			OrderFixture.markDelivered(deliveredOrder);
			OrderFixture.markItemsStatus(deliveredOrder, OrderItemStatus.DELIVERED);
			orderRepository.saveAndFlush(deliveredOrder);

			// when
			List<Long> result = orderItemRepository.findProductIdsByMemberIdAndStatusIn(member.getId(),
					OrderItemStatus.SOLD);

			// then
			assertThat(result).containsExactly(product.getId());
		}
	}
}
