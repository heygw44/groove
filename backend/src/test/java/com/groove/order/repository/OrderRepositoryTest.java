package com.groove.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderExpirationCandidate;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.DataJpaTestSupport;

class OrderRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("주문을 저장하면 항목도 함께 저장된다")
		void cascadesItemsOnSave() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-save@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product createdProduct = ProductFixture.create(artist);
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);
			Order order = OrderFixture.createWithItem(member, product, 2);

			// when
			Order saved = orderRepository.saveAndFlush(order);

			// then
			assertThat(saved.getId()).isNotNull();
			assertThat(saved.getItems()).hasSize(1);
			assertThat(saved.getItems().get(0).getId()).isNotNull();
		}

		@Test
		@DisplayName("order_number 가 중복되면 DataIntegrityViolationException 이 발생한다")
		void throwsWhenOrderNumberDuplicated() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-dup@groove.com"));
			String duplicateOrderNumber = "20260903-DUPLIC01";
			orderRepository.saveAndFlush(OrderFixture.create(member, duplicateOrderNumber));
			Order duplicate = OrderFixture.create(member, duplicateOrderNumber);

			// when & then
			assertThatThrownBy(() -> orderRepository.saveAndFlush(duplicate))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Nested
	@DisplayName("findWithItemsById()")
	class FindWithItemsById {

		@Test
		@DisplayName("항목과 상품 스냅샷을 함께 조회한다")
		void returnsOrderWithItemsAndSnapshot() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-items@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product createdProduct = ProductFixture.create(artist, "Kind of Blue");
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);
			Order saved = orderRepository.saveAndFlush(OrderFixture.createWithItem(member, product, 3));

			// when
			Optional<Order> found = orderRepository.findWithItemsById(saved.getId());

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getItems()).hasSize(1);
			assertThat(found.get().getItems().get(0).getProductName()).isEqualTo("Kind of Blue");
			assertThat(found.get().getItems().get(0).getQuantity()).isEqualTo(3);
		}
	}

	@Nested
	@DisplayName("findByIdAndMemberId()")
	class FindByIdAndMemberId {

		@Test
		@DisplayName("다른 회원의 id 로 조회하면 empty 를 반환한다")
		void returnsEmptyForOtherMember() {
			// given
			Member owner = memberRepository.save(MemberFixture.create("order-owner@groove.com"));
			Member other = memberRepository.save(MemberFixture.create("order-other@groove.com"));
			Order saved = orderRepository.saveAndFlush(OrderFixture.create(owner, "20260903-OTHER001"));

			// when
			Optional<Order> found = orderRepository.findByIdAndMemberId(saved.getId(), other.getId());

			// then
			assertThat(found).isEmpty();
		}
	}

	@Nested
	@DisplayName("findWithItemsByIdAndMemberId()")
	class FindWithItemsByIdAndMemberId {

		@Test
		@DisplayName("본인 주문이면 항목과 함께 조회한다")
		void returnsOrderWithItemsForOwner() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-scoped-owner@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product createdProduct = ProductFixture.create(artist);
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);
			Order saved = orderRepository.saveAndFlush(OrderFixture.createWithItem(member, product, 1));

			// when
			Optional<Order> found = orderRepository.findWithItemsByIdAndMemberId(saved.getId(), member.getId());

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getItems()).hasSize(1);
		}

		@Test
		@DisplayName("다른 회원의 id 로 조회하면 empty 를 반환한다")
		void returnsEmptyForOtherMember() {
			// given
			Member owner = memberRepository.save(MemberFixture.create("order-scoped-target@groove.com"));
			Member other = memberRepository.save(MemberFixture.create("order-scoped-other@groove.com"));
			Order saved = orderRepository.saveAndFlush(OrderFixture.create(owner, "20260903-SCOPED01"));

			// when
			Optional<Order> found = orderRepository.findWithItemsByIdAndMemberId(saved.getId(), other.getId());

			// then
			assertThat(found).isEmpty();
		}
	}

	@Nested
	@DisplayName("findExpirationCandidates()")
	class FindExpirationCandidates {

		private static final LocalDateTime INITIAL_CURSOR = LocalDateTime.of(1970, 1, 1, 0, 0);

		@Test
		@DisplayName("PENDING 이고 만료 시각이 지났으면 대상에 포함하고 아니면 제외한다")
		void includesOnlyExpiredPendingOrders() {
			// given
			LocalDateTime now = LocalDateTime.of(2001, 1, 1, 0, 0);
			Member member = memberRepository.save(MemberFixture.create("order-repo-expire@groove.com"));
			Order expired = saveOrder(member, now.minusMinutes(1));
			Order boundary = saveOrder(member, now);
			Order notYet = saveOrder(member, now.plusMinutes(1));
			Order paid = OrderFixture.markPaid(OrderFixture.withExpiresAt(newOrder(member), now.minusMinutes(1)));
			orderRepository.saveAndFlush(paid);

			// when
			List<OrderExpirationCandidate> result = orderRepository.findExpirationCandidates(OrderStatus.PENDING,
					now, PaymentStatus.UNRESOLVED, INITIAL_CURSOR, 0L, Limit.of(100));

			// then
			assertThat(result).contains(new OrderExpirationCandidate(expired.getId(), now.minusMinutes(1)),
					new OrderExpirationCandidate(boundary.getId(), now));
			assertThat(result).extracting(OrderExpirationCandidate::id)
					.doesNotContain(notYet.getId(), paid.getId());
		}

		@Test
		@DisplayName("결제가 READY/UNKNOWN 이면 대상에서 제외하고 그 밖의 결제 상태면 포함한다")
		void excludesOrdersWithUnresolvedPayment() {
			// given
			LocalDateTime now = LocalDateTime.of(2001, 2, 1, 0, 0);
			Member member = memberRepository.save(MemberFixture.create("order-repo-expire-payment@groove.com"));
			Order ready = saveOrder(member, now.minusMinutes(3));
			paymentRepository.saveAndFlush(Payment.ready(ready));
			Order unknown = saveOrder(member, now.minusMinutes(2));
			paymentRepository.saveAndFlush(PaymentFixture.unknown(unknown, "timeout"));
			Order failed = saveOrder(member, now.minusMinutes(1));
			paymentRepository.saveAndFlush(PaymentFixture.failed(failed, "declined"));

			// when
			List<OrderExpirationCandidate> result = orderRepository.findExpirationCandidates(OrderStatus.PENDING,
					now, PaymentStatus.UNRESOLVED, INITIAL_CURSOR, 0L, Limit.of(100));

			// then
			assertThat(result).extracting(OrderExpirationCandidate::id)
					.contains(failed.getId())
					.doesNotContain(ready.getId(), unknown.getId());
		}

		@Test
		@DisplayName("커서 뒤의 후보만 만료 시각, id 순으로 돌려주고 같은 시각이면 커서 id 보다 큰 행만 포함한다")
		void returnsOnlyCandidatesAfterCursorInOrder() {
			// given
			LocalDateTime now = LocalDateTime.of(2001, 3, 1, 0, 0);
			LocalDateTime early = now.minusHours(3);
			LocalDateTime tie = now.minusHours(2);
			LocalDateTime late = now.minusHours(1);
			Member member = memberRepository.save(MemberFixture.create("order-repo-expire-cursor@groove.com"));
			// 저장 순서상 id 는 late < tieFirst < tieSecond < early 지만 정렬은 만료 시각이 우선이다.
			Long lateId = saveOrder(member, late).getId();
			Long tieFirstId = saveOrder(member, tie).getId();
			Long tieSecondId = saveOrder(member, tie).getId();
			Long earlyId = saveOrder(member, early).getId();
			List<Long> ids = List.of(lateId, tieFirstId, tieSecondId, earlyId);

			// when
			List<OrderExpirationCandidate> result = orderRepository.findExpirationCandidates(OrderStatus.PENDING,
					now, PaymentStatus.UNRESOLVED, tie, tieFirstId, Limit.of(100));

			// then
			assertThat(result).filteredOn(candidate -> ids.contains(candidate.id()))
					.containsExactly(new OrderExpirationCandidate(tieSecondId, tie),
							new OrderExpirationCandidate(lateId, late));
		}

		@Test
		@DisplayName("만료 시각, id 순으로 limit 만큼만 돌려준다")
		void returnsPageInOrderWithinLimit() {
			// given
			LocalDateTime now = LocalDateTime.of(2001, 4, 1, 0, 0);
			LocalDateTime tie = now.minusHours(2);
			Member member = memberRepository.save(MemberFixture.create("order-repo-expire-limit@groove.com"));
			saveOrder(member, now.minusHours(1));
			Long tieFirstId = saveOrder(member, tie).getId();
			Long tieSecondId = saveOrder(member, tie).getId();

			// when
			List<OrderExpirationCandidate> result = orderRepository.findExpirationCandidates(OrderStatus.PENDING,
					now, PaymentStatus.UNRESOLVED, tie.minusSeconds(1), 0L, Limit.of(2));

			// then
			assertThat(result).containsExactly(new OrderExpirationCandidate(tieFirstId, tie),
					new OrderExpirationCandidate(tieSecondId, tie));
		}

		private Order saveOrder(Member member, LocalDateTime expiresAt) {
			return orderRepository.saveAndFlush(OrderFixture.withExpiresAt(newOrder(member), expiresAt));
		}

		private Order newOrder(Member member) {
			return OrderFixture.create(member, "20010101-EXP" + UUID.randomUUID().toString().substring(0, 8));
		}
	}
}
