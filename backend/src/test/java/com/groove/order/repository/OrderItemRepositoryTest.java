package com.groove.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemClaimStatus;
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
		@DisplayName("PURCHASE_CONFIRMED 상품주문이면 true 를 반환한다")
		void returnsTrueWhenPurchaseConfirmed() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-delivered@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-delivered"));
			Product createdProduct = ProductFixture.create(artist);
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);
			Order order = OrderFixture.createWithItem(member, product, 1);
			OrderFixture.markDelivered(order);
			OrderFixture.markItemsStatus(order, OrderItemStatus.PURCHASE_CONFIRMED);
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
			OrderFixture.markItemsStatus(order, OrderItemStatus.PURCHASE_CONFIRMED);
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

	@Nested
	@DisplayName("findDistinctOrderIdsByIdIn()")
	class FindDistinctOrderIdsByIdIn {

		@Test
		@DisplayName("여러 상품주문이 같은 주문에 속하면 주문 id 를 중복 없이 반환한다")
		void returnsDistinctOrderIds() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-orderids@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-orderids"));
			Product productA = productRepository.save(persistableProduct(artist, "잠금순서 상품 A"));
			Product productB = productRepository.save(persistableProduct(artist, "잠금순서 상품 B"));
			Order order = OrderFixture.createWithItems(member, List.of(productA, productB));
			orderRepository.saveAndFlush(order);
			List<Long> itemIds = order.getItems().stream().map(OrderItem::getId).toList();

			// when
			List<Long> result = orderItemRepository.findDistinctOrderIdsByIdIn(itemIds);

			// then
			assertThat(result).containsExactly(order.getId());
		}
	}

	@Nested
	@DisplayName("findOrderIdById()")
	class FindOrderIdById {

		@Test
		@DisplayName("상품주문이 속한 주문 id 를 반환한다")
		void returnsOrderId() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-orderid@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-orderid"));
			Product product = productRepository.save(persistableProduct(artist, "단건 조회 상품"));
			Order order = OrderFixture.createWithItem(member, product, 1);
			orderRepository.saveAndFlush(order);
			Long itemId = order.getItems().get(0).getId();

			// when
			Optional<Long> result = orderItemRepository.findOrderIdById(itemId);

			// then
			assertThat(result).contains(order.getId());
		}

		@Test
		@DisplayName("존재하지 않는 id 면 빈 값을 반환한다")
		void returnsEmptyWhenNotFound() {
			// when
			Optional<Long> result = orderItemRepository.findOrderIdById(-1L);

			// then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("findIdsByStatusAndShippedAtBefore()")
	class FindIdsByStatusAndShippedAtBefore {

		@Test
		@DisplayName("SHIPPING 이고 발송 시각이 cutoff 이전이면 대상에 포함한다")
		void includesShippedBeforeCutoff() {
			// given
			LocalDateTime cutoff = LocalDateTime.of(2033, 6, 1, 0, 0);
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-autodeliver@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-autodeliver"));
			Product product = productRepository.save(persistableProduct(artist, "자동배송완료 대상 상품"));
			Order order = OrderFixture.createWithItem(member, product, 1);
			OrderFixture.markItemsStatus(order, OrderItemStatus.SHIPPING);
			OrderFixture.markFirstItemShippedAt(order, cutoff.minusDays(1));
			orderRepository.saveAndFlush(order);
			Long itemId = order.getItems().get(0).getId();

			// when
			List<Long> result = orderItemRepository.findIdsByStatusAndShippedAtBefore(OrderItemStatus.SHIPPING,
					cutoff, Limit.of(100));

			// then
			assertThat(result).contains(itemId);
		}

		@Test
		@DisplayName("발송 시각이 cutoff 이후면 대상에서 제외한다")
		void excludesShippedAfterCutoff() {
			// given
			LocalDateTime cutoff = LocalDateTime.of(2033, 6, 2, 0, 0);
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-autodeliver2@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-autodeliver2"));
			Product product = productRepository.save(persistableProduct(artist, "자동배송완료 제외 상품"));
			Order order = OrderFixture.createWithItem(member, product, 1);
			OrderFixture.markItemsStatus(order, OrderItemStatus.SHIPPING);
			OrderFixture.markFirstItemShippedAt(order, cutoff.plusDays(1));
			orderRepository.saveAndFlush(order);
			Long itemId = order.getItems().get(0).getId();

			// when
			List<Long> result = orderItemRepository.findIdsByStatusAndShippedAtBefore(OrderItemStatus.SHIPPING,
					cutoff, Limit.of(100));

			// then
			assertThat(result).doesNotContain(itemId);
		}
	}

	@Nested
	@DisplayName("findIdsByStatusAndDeliveredAtBeforeAndClaimNotInProgress()")
	class FindIdsByStatusAndDeliveredAtBeforeAndClaimNotInProgress {

		@Test
		@DisplayName("DELIVERED 이고 배송완료 시각이 cutoff 이전이며 클레임이 없으면 대상에 포함한다")
		void includesDeliveredWithoutClaim() {
			// given
			LocalDateTime cutoff = LocalDateTime.of(2033, 7, 1, 0, 0);
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-autoconfirm@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-autoconfirm"));
			Product product = productRepository.save(persistableProduct(artist, "자동구매확정 대상 상품"));
			Order order = OrderFixture.createWithItem(member, product, 1);
			OrderFixture.markItemsStatus(order, OrderItemStatus.DELIVERED);
			OrderFixture.markFirstItemDeliveredAt(order, cutoff.minusDays(1));
			orderRepository.saveAndFlush(order);
			Long itemId = order.getItems().get(0).getId();

			// when
			List<Long> result = orderItemRepository.findIdsByStatusAndDeliveredAtBeforeAndClaimNotInProgress(
					OrderItemStatus.DELIVERED, cutoff,
					List.of(OrderItemClaimStatus.CANCEL_REQUEST, OrderItemClaimStatus.RETURN_REQUEST,
							OrderItemClaimStatus.COLLECTING),
					Limit.of(100));

			// then
			assertThat(result).contains(itemId);
		}

		@Test
		@DisplayName("진행 중인 반품 클레임이 있으면 대상에서 제외한다")
		void excludesDeliveredWithInProgressClaim() {
			// given
			LocalDateTime cutoff = LocalDateTime.of(2033, 7, 2, 0, 0);
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-autoconfirm2@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-autoconfirm2"));
			Product product = productRepository.save(persistableProduct(artist, "자동구매확정 제외 상품"));
			Order order = OrderFixture.createWithItem(member, product, 1);
			OrderFixture.markItemsStatus(order, OrderItemStatus.DELIVERED);
			OrderFixture.markFirstItemDeliveredAt(order, cutoff.minusDays(1));
			OrderFixture.markFirstItemClaimStatus(order, OrderItemClaimStatus.RETURN_REQUEST);
			orderRepository.saveAndFlush(order);
			Long itemId = order.getItems().get(0).getId();

			// when
			List<Long> result = orderItemRepository.findIdsByStatusAndDeliveredAtBeforeAndClaimNotInProgress(
					OrderItemStatus.DELIVERED, cutoff,
					List.of(OrderItemClaimStatus.CANCEL_REQUEST, OrderItemClaimStatus.RETURN_REQUEST,
							OrderItemClaimStatus.COLLECTING),
					Limit.of(100));

			// then
			assertThat(result).doesNotContain(itemId);
		}
	}

	private Product persistableProduct(Artist artist, String title) {
		Product product = ProductFixture.create(artist, title);
		albumRepository.save(product.getAlbum());
		return product;
	}
}
