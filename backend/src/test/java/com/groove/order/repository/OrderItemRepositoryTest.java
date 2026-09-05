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
import com.groove.order.entity.OrderStatus;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
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

	@Nested
	@DisplayName("existsByOrderMemberIdAndProductIdAndOrderStatus()")
	class ExistsByOrderMemberIdAndProductIdAndOrderStatus {

		@Test
		@DisplayName("DELIVERED 주문에 포함된 상품이면 true 를 반환한다")
		void returnsTrueWhenDelivered() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-delivered@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-delivered"));
			Product product = productRepository.save(ProductFixture.create(artist));
			Order order = OrderFixture.markDelivered(OrderFixture.createWithItem(member, product, 1));
			orderRepository.saveAndFlush(order);

			// when
			boolean exists = orderItemRepository.existsByOrderMemberIdAndProductIdAndOrderStatus(member.getId(),
					product.getId(), OrderStatus.DELIVERED);

			// then
			assertThat(exists).isTrue();
		}

		@Test
		@DisplayName("PAID 상태면 false 를 반환한다")
		void returnsFalseWhenPaid() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-paid@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-paid"));
			Product product = productRepository.save(ProductFixture.create(artist));
			Order order = OrderFixture.createWithItem(member, product, 1);
			order.markPaid();
			orderRepository.saveAndFlush(order);

			// when
			boolean exists = orderItemRepository.existsByOrderMemberIdAndProductIdAndOrderStatus(member.getId(),
					product.getId(), OrderStatus.DELIVERED);

			// then
			assertThat(exists).isFalse();
		}

		@Test
		@DisplayName("다른 상품이면 false 를 반환한다")
		void returnsFalseForOtherProduct() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-other@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-other"));
			Product deliveredProduct = productRepository.save(ProductFixture.create(artist));
			Product otherProduct = productRepository.save(ProductFixture.create(artist, "다른 상품"));
			Order order = OrderFixture.markDelivered(OrderFixture.createWithItem(member, deliveredProduct, 1));
			orderRepository.saveAndFlush(order);

			// when
			boolean exists = orderItemRepository.existsByOrderMemberIdAndProductIdAndOrderStatus(member.getId(),
					otherProduct.getId(), OrderStatus.DELIVERED);

			// then
			assertThat(exists).isFalse();
		}
	}

	@Nested
	@DisplayName("findProductIdsByMemberIdAndOrderStatusIn()")
	class FindProductIdsByMemberIdAndOrderStatusIn {

		@Test
		@DisplayName("PAID·DELIVERED 주문에 담긴 상품 id 를 반환한다")
		void returnsProductIdsForPaidOrLaterOrders() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-status@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-status"));
			Product paidProduct = productRepository.save(ProductFixture.create(artist, "결제완료 상품"));
			Product deliveredProduct = productRepository.save(ProductFixture.create(artist, "배송완료 상품"));
			Product pendingProduct = productRepository.save(ProductFixture.create(artist, "결제대기 상품"));

			Order paidOrder = OrderFixture.createWithItems(member, List.of(paidProduct));
			paidOrder.markPaid();
			orderRepository.saveAndFlush(paidOrder);
			orderRepository.saveAndFlush(
					OrderFixture.markDelivered(OrderFixture.createWithItems(member, List.of(deliveredProduct))));
			orderRepository.saveAndFlush(OrderFixture.createWithItems(member, List.of(pendingProduct)));

			// when
			List<Long> result = orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(member.getId(),
					OrderStatus.PAID_OR_LATER);

			// then
			assertThat(result).containsExactlyInAnyOrder(paidProduct.getId(), deliveredProduct.getId());
		}

		@Test
		@DisplayName("같은 상품이 담긴 주문이 두 개면 중복 없이 한 번만 반환한다")
		void distinctsSameProductAcrossMultipleOrders() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-distinct@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-distinct"));
			Product product = productRepository.save(ProductFixture.create(artist));

			orderRepository.saveAndFlush(OrderFixture.markPaid(OrderFixture.createWithItems(member, List.of(product))));
			orderRepository.saveAndFlush(
					OrderFixture.markDelivered(OrderFixture.createWithItems(member, List.of(product))));

			// when
			List<Long> result = orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(member.getId(),
					OrderStatus.PAID_OR_LATER);

			// then
			assertThat(result).containsExactly(product.getId());
		}
	}
}
