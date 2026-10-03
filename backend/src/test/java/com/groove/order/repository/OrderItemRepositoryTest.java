package com.groove.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderItemConfirmCandidate;
import com.groove.order.dto.OrderItemDeliverCandidate;
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
	@DisplayName("existsByMemberIdAndProductIdAndStatusInExcludingClaims()")
	class ExistsByMemberIdAndProductIdAndStatusInExcludingClaims {

		@Test
		@DisplayName("DELIVERED 이고 클레임이 없으면 true 를 반환한다")
		void returnsTrueWhenDeliveredWithoutClaim() {
			// given
			OrderItem item = saveItem("no-claim", OrderItemStatus.DELIVERED, null);

			// when
			boolean exists = existsAwaitingConfirm(item);

			// then
			assertThat(exists).isTrue();
		}

		@ParameterizedTest
		@EnumSource(value = OrderItemClaimStatus.class, names = {"CANCEL_REQUEST", "RETURN_REQUEST", "COLLECTING"})
		@DisplayName("DELIVERED 여도 진행 중인 클레임이 있으면 false 를 반환한다")
		void returnsFalseWhenClaimInProgress(OrderItemClaimStatus claimStatus) {
			// given
			OrderItem item = saveItem("in-progress-" + claimStatus.ordinal(), OrderItemStatus.DELIVERED, claimStatus);

			// when
			boolean exists = existsAwaitingConfirm(item);

			// then
			assertThat(exists).isFalse();
		}

		@Test
		@DisplayName("DELIVERED 이고 끝난 클레임만 있으면 true 를 반환한다")
		void returnsTrueWhenClaimFinished() {
			// given
			OrderItem item = saveItem("finished-claim", OrderItemStatus.DELIVERED, OrderItemClaimStatus.RETURN_REJECT);

			// when
			boolean exists = existsAwaitingConfirm(item);

			// then
			assertThat(exists).isTrue();
		}

		@Test
		@DisplayName("상태가 목록에 없으면 false 를 반환한다")
		void returnsFalseWhenStatusNotInList() {
			// given
			OrderItem item = saveItem("confirmed", OrderItemStatus.PURCHASE_CONFIRMED, null);

			// when
			boolean exists = existsAwaitingConfirm(item);

			// then
			assertThat(exists).isFalse();
		}

		private boolean existsAwaitingConfirm(OrderItem item) {
			return orderItemRepository.existsByMemberIdAndProductIdAndStatusInExcludingClaims(
					item.getOrder().getMember().getId(), item.getProduct().getId(),
					OrderItemStatus.AWAITING_PURCHASE_CONFIRM, OrderItemClaimStatus.IN_PROGRESS);
		}

		private OrderItem saveItem(String key, OrderItemStatus status, OrderItemClaimStatus claimStatus) {
			Member member = memberRepository.save(
					MemberFixture.create("order-item-repo-awaiting-" + key + "@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-awaiting-" + key));
			Product product = productRepository.save(persistableProduct(artist, "구매확정 대기 " + key));
			Order order = OrderFixture.createWithItem(member, product, 1);
			OrderFixture.markItemsStatus(order, status);
			if (claimStatus != null) {
				OrderFixture.markFirstItemClaimStatus(order, claimStatus);
			}
			orderRepository.saveAndFlush(order);
			return order.getItems().get(0);
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
	@DisplayName("findDeliverCandidates()")
	class FindDeliverCandidates {

		private static final LocalDateTime INITIAL_CURSOR = LocalDateTime.of(1970, 1, 1, 0, 0);

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
			List<OrderItemDeliverCandidate> result = orderItemRepository.findDeliverCandidates(
					OrderItemStatus.SHIPPING, cutoff, INITIAL_CURSOR, 0L, Limit.of(100));

			// then
			assertThat(result).contains(new OrderItemDeliverCandidate(itemId, cutoff.minusDays(1)));
		}

		@Test
		@DisplayName("발송 시각이 cutoff 이후이거나 SHIPPING 이 아니면 대상에서 제외한다")
		void excludesAfterCutoffOrNotShipping() {
			// given
			LocalDateTime cutoff = LocalDateTime.of(2033, 6, 2, 0, 0);
			List<Long> ids = saveShippingItems("autodeliver2", cutoff.plusDays(1), cutoff.minusDays(1));
			OrderItem delivered = orderItemRepository.findById(ids.get(1)).orElseThrow();
			ReflectionTestUtils.setField(delivered, "status", OrderItemStatus.DELIVERED);
			orderItemRepository.saveAndFlush(delivered);

			// when
			List<OrderItemDeliverCandidate> result = orderItemRepository.findDeliverCandidates(
					OrderItemStatus.SHIPPING, cutoff, INITIAL_CURSOR, 0L, Limit.of(100));

			// then
			assertThat(result).extracting(OrderItemDeliverCandidate::id).doesNotContainAnyElementsOf(ids);
		}

		@Test
		@DisplayName("커서 뒤의 후보만 발송 시각, id 순으로 돌려주고 같은 시각이면 커서 id 보다 큰 행만 포함한다")
		void returnsOnlyCandidatesAfterCursorInOrder() {
			// given
			LocalDateTime cutoff = LocalDateTime.of(2033, 6, 10, 0, 0);
			LocalDateTime early = cutoff.minusDays(3);
			LocalDateTime tie = cutoff.minusDays(2);
			LocalDateTime late = cutoff.minusDays(1);
			// 저장 순서상 id 는 late < tieFirst < tieSecond < early 지만 정렬은 발송 시각이 우선이다.
			List<Long> ids = saveShippingItems("autodeliver-cursor", late, tie, tie, early);
			Long lateId = ids.get(0);
			Long tieFirstId = ids.get(1);
			Long tieSecondId = ids.get(2);

			// when
			List<OrderItemDeliverCandidate> result = orderItemRepository.findDeliverCandidates(
					OrderItemStatus.SHIPPING, cutoff, tie, tieFirstId, Limit.of(100));

			// then
			assertThat(result).filteredOn(candidate -> ids.contains(candidate.id()))
					.containsExactly(new OrderItemDeliverCandidate(tieSecondId, tie),
							new OrderItemDeliverCandidate(lateId, late));
		}

		@Test
		@DisplayName("커서보다 앞선 행이 없으면 발송 시각, id 순으로 limit 만큼만 돌려준다")
		void returnsPageInOrderWithinLimit() {
			// given
			LocalDateTime cutoff = LocalDateTime.of(2033, 6, 20, 0, 0);
			LocalDateTime tie = cutoff.minusDays(2);
			LocalDateTime late = cutoff.minusDays(1);
			List<Long> ids = saveShippingItems("autodeliver-first", late, tie, tie);

			// when
			List<OrderItemDeliverCandidate> result = orderItemRepository.findDeliverCandidates(
					OrderItemStatus.SHIPPING, cutoff, tie.minusSeconds(1), 0L, Limit.of(2));

			// then
			assertThat(result).containsExactly(new OrderItemDeliverCandidate(ids.get(1), tie),
					new OrderItemDeliverCandidate(ids.get(2), tie));
		}

		private List<Long> saveShippingItems(String key, LocalDateTime... shippedAts) {
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-" + key + "@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-" + key));
			List<Product> products = new ArrayList<>();
			for (int i = 0; i < shippedAts.length; i++) {
				products.add(productRepository.save(persistableProduct(artist, key + " 상품 " + i)));
			}
			Order order = OrderFixture.createWithItems(member, products);
			OrderFixture.markItemsStatus(order, OrderItemStatus.SHIPPING);
			for (int i = 0; i < shippedAts.length; i++) {
				ReflectionTestUtils.setField(order.getItems().get(i), "shippedAt", shippedAts[i]);
			}
			orderRepository.saveAndFlush(order);
			return order.getItems().stream().map(OrderItem::getId).toList();
		}
	}

	@Nested
	@DisplayName("findConfirmCandidates()")
	class FindConfirmCandidates {

		private static final LocalDateTime INITIAL_CURSOR = LocalDateTime.of(1970, 1, 1, 0, 0);
		private static final List<OrderItemClaimStatus> IN_PROGRESS = List.of(OrderItemClaimStatus.CANCEL_REQUEST,
				OrderItemClaimStatus.RETURN_REQUEST, OrderItemClaimStatus.COLLECTING);

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
			List<OrderItemConfirmCandidate> result = orderItemRepository.findConfirmCandidates(
					OrderItemStatus.DELIVERED, cutoff, IN_PROGRESS, INITIAL_CURSOR, 0L, Limit.of(100));

			// then
			assertThat(result).contains(new OrderItemConfirmCandidate(itemId, cutoff.minusDays(1)));
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
			List<OrderItemConfirmCandidate> result = orderItemRepository.findConfirmCandidates(
					OrderItemStatus.DELIVERED, cutoff, IN_PROGRESS, INITIAL_CURSOR, 0L, Limit.of(100));

			// then
			assertThat(result).extracting(OrderItemConfirmCandidate::id).doesNotContain(itemId);
		}

		@Test
		@DisplayName("배송완료 시각이 cutoff 이후이거나 DELIVERED 가 아니면 대상에서 제외한다")
		void excludesAfterCutoffOrNotDelivered() {
			// given
			LocalDateTime cutoff = LocalDateTime.of(2033, 7, 3, 0, 0);
			List<Long> ids = saveDeliveredItems("autoconfirm3", cutoff.plusDays(1), cutoff.minusDays(1));
			OrderItem shipping = orderItemRepository.findById(ids.get(1)).orElseThrow();
			ReflectionTestUtils.setField(shipping, "status", OrderItemStatus.SHIPPING);
			orderItemRepository.saveAndFlush(shipping);

			// when
			List<OrderItemConfirmCandidate> result = orderItemRepository.findConfirmCandidates(
					OrderItemStatus.DELIVERED, cutoff, IN_PROGRESS, INITIAL_CURSOR, 0L, Limit.of(100));

			// then
			assertThat(result).extracting(OrderItemConfirmCandidate::id).doesNotContainAnyElementsOf(ids);
		}

		@Test
		@DisplayName("커서 뒤의 후보만 배송완료 시각, id 순으로 돌려주고 같은 시각이면 커서 id 보다 큰 행만 포함한다")
		void returnsOnlyCandidatesAfterCursorInOrder() {
			// given
			LocalDateTime cutoff = LocalDateTime.of(2033, 8, 1, 0, 0);
			LocalDateTime early = cutoff.minusDays(3);
			LocalDateTime tie = cutoff.minusDays(2);
			LocalDateTime late = cutoff.minusDays(1);
			// 저장 순서상 id 는 late < tieFirst < tieSecond < early 지만 정렬은 배송완료 시각이 우선이다.
			List<Long> ids = saveDeliveredItems("autoconfirm-cursor", late, tie, tie, early);
			Long lateId = ids.get(0);
			Long tieFirstId = ids.get(1);
			Long tieSecondId = ids.get(2);

			// when
			List<OrderItemConfirmCandidate> result = orderItemRepository.findConfirmCandidates(
					OrderItemStatus.DELIVERED, cutoff, IN_PROGRESS, tie, tieFirstId, Limit.of(100));

			// then
			assertThat(result).filteredOn(candidate -> ids.contains(candidate.id()))
					.containsExactly(new OrderItemConfirmCandidate(tieSecondId, tie),
							new OrderItemConfirmCandidate(lateId, late));
		}

		@Test
		@DisplayName("커서보다 앞선 행이 없으면 배송완료 시각, id 순으로 limit 만큼만 돌려준다")
		void returnsPageInOrderWithinLimit() {
			// given
			LocalDateTime cutoff = LocalDateTime.of(2033, 9, 1, 0, 0);
			LocalDateTime tie = cutoff.minusDays(2);
			LocalDateTime late = cutoff.minusDays(1);
			List<Long> ids = saveDeliveredItems("autoconfirm-first", late, tie, tie);

			// when
			List<OrderItemConfirmCandidate> result = orderItemRepository.findConfirmCandidates(
					OrderItemStatus.DELIVERED, cutoff, IN_PROGRESS, tie.minusSeconds(1), 0L, Limit.of(2));

			// then
			assertThat(result).containsExactly(new OrderItemConfirmCandidate(ids.get(1), tie),
					new OrderItemConfirmCandidate(ids.get(2), tie));
		}

		private List<Long> saveDeliveredItems(String key, LocalDateTime... deliveredAts) {
			Member member = memberRepository.save(MemberFixture.create("order-item-repo-" + key + "@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("order-item-repo-" + key));
			List<Product> products = new ArrayList<>();
			for (int i = 0; i < deliveredAts.length; i++) {
				products.add(productRepository.save(persistableProduct(artist, key + " 상품 " + i)));
			}
			Order order = OrderFixture.createWithItems(member, products);
			OrderFixture.markItemsStatus(order, OrderItemStatus.DELIVERED);
			for (int i = 0; i < deliveredAts.length; i++) {
				ReflectionTestUtils.setField(order.getItems().get(i), "deliveredAt", deliveredAts[i]);
			}
			orderRepository.saveAndFlush(order);
			return order.getItems().stream().map(OrderItem::getId).toList();
		}
	}

	private Product persistableProduct(Artist artist, String title) {
		Product product = ProductFixture.create(artist, title);
		albumRepository.save(product.getAlbum());
		return product;
	}
}
