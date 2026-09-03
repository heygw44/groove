package com.groove.order.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.order.dto.OrderSearchCondition;
import com.groove.order.dto.OrderSummaryResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.support.MybatisTestSupport;

import jakarta.persistence.EntityManager;

/** 공유 테스트 DB 에 다른 테스트가 남긴 주문이 섞이므로 자기 member id 로만 단언한다. */
class OrderQueryMapperTest extends MybatisTestSupport {

	@Autowired
	private OrderQueryMapper orderQueryMapper;

	@Autowired
	private EntityManager em;

	private Member owner;
	private Member other;
	private Artist artist;
	private Product kindOfBlue;
	private Product loveSupreme;

	@BeforeEach
	void setUp() {
		owner = MemberFixture.create("order-query-owner@groove.com");
		other = MemberFixture.create("order-query-other@groove.com");
		artist = ArtistFixture.create();
		em.persist(owner);
		em.persist(other);
		em.persist(artist);

		kindOfBlue = ProductFixture.create(artist, "OQM Kind of Blue");
		kindOfBlue.addImage("https://cdn.groove.com/kind-of-blue-0.jpg", 0);
		loveSupreme = ProductFixture.create(artist, "OQM A Love Supreme");
		em.persist(kindOfBlue);
		em.persist(loveSupreme);
	}

	private Order persistOrder(Member member, String orderNumber, Product product, int quantity) {
		Order order = OrderFixture.create(member, orderNumber);
		order.addItem(product, quantity);
		em.persist(order);
		em.flush();
		return order;
	}

	private static OrderSearchCondition condition(Long memberId, OrderStatus status, int page, int size) {
		return new OrderSearchCondition(memberId, status, page, size);
	}

	@Nested
	@DisplayName("findMyOrders()")
	class FindMyOrders {

		@Test
		@DisplayName("다른 회원의 주문은 조회되지 않는다")
		void excludesOtherMemberOrders() {
			// given
			Order ownerOrder = persistOrder(owner, "20260903-OQM00001", kindOfBlue, 1);
			persistOrder(other, "20260903-OQM00002", loveSupreme, 1);
			em.clear();

			// when
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20));

			// then
			assertThat(result).extracting(OrderSummaryResponse::id).containsExactly(ownerOrder.getId());
		}

		@Test
		@DisplayName("status 로 필터링하면 해당 상태의 주문만 반환한다")
		void filtersByStatus() {
			// given
			Order pendingOrder = persistOrder(owner, "20260903-OQM00003", kindOfBlue, 1);
			Order canceledOrder = persistOrder(owner, "20260903-OQM00004", loveSupreme, 1);
			canceledOrder.cancel("단순 변심");
			em.flush();
			em.clear();

			// when
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), OrderStatus.CANCELED, 0, 20));

			// then
			assertThat(result).extracting(OrderSummaryResponse::id).containsExactly(canceledOrder.getId());
			assertThat(result).extracting(OrderSummaryResponse::status).containsOnly(OrderStatus.CANCELED);
			assertThat(pendingOrder).isNotNull();
		}

		@Test
		@DisplayName("최신순(created_at DESC, id DESC)으로 반환한다")
		void sortsByLatest() {
			// given
			Order first = persistOrder(owner, "20260903-OQM00005", kindOfBlue, 1);
			Order second = persistOrder(owner, "20260903-OQM00006", kindOfBlue, 1);
			Order third = persistOrder(owner, "20260903-OQM00007", kindOfBlue, 1);
			em.clear();

			// when
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20));

			// then
			assertThat(result).extracting(OrderSummaryResponse::id)
					.containsExactly(third.getId(), second.getId(), first.getId());
		}

		@Test
		@DisplayName("size·page 로 페이징하면 offset 이후 항목만 반환한다")
		void paginatesWithOffset() {
			// given: 최신순 정렬이므로 가장 먼저 저장한 주문이 두 번째 페이지로 밀려난다
			Order oldest = persistOrder(owner, "20260903-OQM00008", kindOfBlue, 1);
			persistOrder(owner, "20260903-OQM00009", kindOfBlue, 1);
			persistOrder(owner, "20260903-OQM00010", kindOfBlue, 1);
			em.clear();

			// when
			List<OrderSummaryResponse> secondPage = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 1, 2));

			// then
			assertThat(secondPage).extracting(OrderSummaryResponse::id).containsExactly(oldest.getId());
		}

		@Test
		@DisplayName("대표 상품명과 상품 수를 함께 반환한다")
		void returnsRepresentativeProductNameAndItemCount() {
			// given
			Order order = OrderFixture.create(owner, "20260903-OQM00011");
			order.addItem(kindOfBlue, 1);
			order.addItem(loveSupreme, 2);
			em.persist(order);
			em.flush();
			em.clear();

			// when
			OrderSummaryResponse result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20)).stream()
					.filter(summary -> summary.id().equals(order.getId()))
					.findFirst()
					.orElseThrow();

			// then
			assertThat(result.representativeProductName()).isEqualTo("OQM Kind of Blue");
			assertThat(result.itemCount()).isEqualTo(2);
		}

		@Test
		@DisplayName("대표 상품에 sort_order 0 이미지가 있으면 썸네일 URL 을 반환한다")
		void returnsThumbnailWhenRepresentativeProductHasImage() {
			// given
			Order order = persistOrder(owner, "20260903-OQM00012", kindOfBlue, 1);
			em.clear();

			// when
			OrderSummaryResponse result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20)).stream()
					.filter(summary -> summary.id().equals(order.getId()))
					.findFirst()
					.orElseThrow();

			// then
			assertThat(result.thumbnailUrl()).isEqualTo("https://cdn.groove.com/kind-of-blue-0.jpg");
		}

		@Test
		@DisplayName("대표 상품에 이미지가 없으면 썸네일 URL 이 null 이다")
		void returnsNullThumbnailWhenNoImage() {
			// given
			Order order = persistOrder(owner, "20260903-OQM00013", loveSupreme, 1);
			em.clear();

			// when
			OrderSummaryResponse result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20)).stream()
					.filter(summary -> summary.id().equals(order.getId()))
					.findFirst()
					.orElseThrow();

			// then
			assertThat(result.thumbnailUrl()).isNull();
		}
	}

	@Nested
	@DisplayName("countMyOrders()")
	class CountMyOrders {

		@Test
		@DisplayName("동일 조건의 findMyOrders 결과 개수와 일치한다")
		void matchesFindResultSize() {
			// given
			persistOrder(owner, "20260903-OQM00014", kindOfBlue, 1);
			persistOrder(owner, "20260903-OQM00015", kindOfBlue, 1);
			em.clear();

			// when
			long count = orderQueryMapper.countMyOrders(condition(owner.getId(), null, 0, 20));
			List<OrderSummaryResponse> result = orderQueryMapper.findMyOrders(
					condition(owner.getId(), null, 0, 20));

			// then
			assertThat(count).isEqualTo(result.size());
		}
	}
}
