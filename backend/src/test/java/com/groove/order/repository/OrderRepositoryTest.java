package com.groove.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.DataJpaTestSupport;

class OrderRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private OrderRepository orderRepository;

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
		@DisplayName("주문을 저장하면 항목도 함께 저장된다")
		void cascadesItemsOnSave() {
			// given
			Member member = memberRepository.save(MemberFixture.create("order-save@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
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
			Product product = productRepository.save(ProductFixture.create(artist, "Kind of Blue"));
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
}
