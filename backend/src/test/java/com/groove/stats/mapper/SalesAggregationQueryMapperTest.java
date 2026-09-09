package com.groove.stats.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.payment.entity.Payment;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.stats.dto.DailySalesAggregateRow;
import com.groove.stats.dto.ProductSalesAggregateRow;
import com.groove.support.MybatisTestSupport;

import jakarta.persistence.EntityManager;

/** 공유 테스트 DB 에 다른 테스트가 남긴 결제/주문이 섞이므로 이 테스트만 쓰는 먼 날짜로만 단언한다. */
class SalesAggregationQueryMapperTest extends MybatisTestSupport {

	private static final LocalDate SALE_DATE = LocalDate.of(2031, 7, 10);

	@Autowired
	private SalesAggregationQueryMapper salesAggregationQueryMapper;

	@Autowired
	private EntityManager em;

	private Member member;
	private Artist artist;

	@BeforeEach
	void setUp() {
		member = MemberFixture.create("sales-aggregate-member@groove.com");
		artist = ArtistFixture.create("SAM Artist");
		em.persist(member);
		em.persist(artist);
	}

	private Product persistProduct(String title, BigDecimal price) {
		Product product = ProductFixture.create(artist, title, price);
		em.persist(product.getAlbum());
		em.persist(product);
		return product;
	}

	private void persistPaidOrder(String orderNumber, Product product, int quantity, String paymentKey,
			LocalDateTime approvedAt) {
		Order order = OrderFixture.create(member, orderNumber);
		order.addItem(product, quantity);
		OrderFixture.markPaid(order);
		em.persist(order);
		em.persist(PaymentFixture.approvedAt(order, paymentKey, approvedAt));
	}

	@Nested
	@DisplayName("findDailySalesOf()")
	class FindDailySalesOf {

		@Test
		@DisplayName("승인일에 매출을, 취소일에 취소액을 하루치로 집계한다")
		void aggregatesApprovedAndCanceledOnSameDate() {
			// given
			Product product = persistProduct("SAM Daily A", new BigDecimal("30000"));

			persistPaidOrder("20310710-SAM00001", product, 1, "sam-daily-key-1",
					LocalDateTime.of(2031, 7, 10, 10, 0));
			persistPaidOrder("20310710-SAM00002", product, 1, "sam-daily-key-2",
					LocalDateTime.of(2031, 7, 10, 15, 0));

			Order canceledOrder = OrderFixture.create(member, "20310709-SAM00003");
			canceledOrder.addItem(product, 1);
			OrderFixture.markPaid(canceledOrder);
			em.persist(canceledOrder);
			Payment canceledPayment = PaymentFixture.canceledAt(canceledOrder, "sam-daily-key-3",
					LocalDateTime.of(2031, 7, 9, 9, 0), LocalDateTime.of(2031, 7, 10, 9, 0));
			em.persist(canceledPayment);

			em.flush();
			em.clear();

			// when
			DailySalesAggregateRow result = salesAggregationQueryMapper.findDailySalesOf(SALE_DATE);

			// then
			assertThat(result.orderCount()).isEqualTo(2);
			assertThat(result.salesAmount()).isEqualByComparingTo(new BigDecimal("60000"));
			assertThat(result.cancelCount()).isEqualTo(1);
			assertThat(result.cancelAmount()).isEqualByComparingTo(new BigDecimal("30000"));
		}

		@Test
		@DisplayName("그 날짜에 판매가 없으면 0으로 채운 결과를 반환한다")
		void returnsZeroWhenNoSalesOnDate() {
			// given
			LocalDate emptyDate = LocalDate.of(2031, 7, 11);

			// when
			DailySalesAggregateRow result = salesAggregationQueryMapper.findDailySalesOf(emptyDate);

			// then
			assertThat(result.orderCount()).isZero();
			assertThat(result.salesAmount()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(result.cancelCount()).isZero();
			assertThat(result.cancelAmount()).isEqualByComparingTo(BigDecimal.ZERO);
		}
	}

	@Nested
	@DisplayName("findProductSalesOf()")
	class FindProductSalesOf {

		@Test
		@DisplayName("하루치 상품별 판매 수량·매출·주문 수를 product_id 오름차순으로 집계한다")
		void aggregatesProductSalesOrderedByProductId() {
			// given
			Product productA = persistProduct("SAM Popular A", new BigDecimal("50000"));
			Product productB = persistProduct("SAM Popular B", new BigDecimal("10000"));

			Order order1 = OrderFixture.create(member, "20310710-SAMPOP001");
			order1.addItem(productA, 2);
			OrderFixture.markPaid(order1);
			em.persist(order1);
			em.persist(PaymentFixture.approvedAt(order1, "sam-pop-key-1", LocalDateTime.of(2031, 7, 10, 10, 0)));

			Order order2 = OrderFixture.create(member, "20310710-SAMPOP002");
			order2.addItem(productA, 1);
			order2.addItem(productB, 3);
			OrderFixture.markDelivered(order2);
			em.persist(order2);
			em.persist(PaymentFixture.approvedAt(order2, "sam-pop-key-2", LocalDateTime.of(2031, 7, 10, 12, 0)));

			em.flush();
			em.clear();

			// when
			List<ProductSalesAggregateRow> result = salesAggregationQueryMapper.findProductSalesOf(SALE_DATE);

			// then
			List<ProductSalesAggregateRow> own = result.stream()
					.filter(row -> row.productId().equals(productA.getId()) || row.productId().equals(productB.getId()))
					.toList();
			assertThat(own).extracting(ProductSalesAggregateRow::productId)
					.containsExactly(sortedIds(productA, productB).get(0), sortedIds(productA, productB).get(1));

			ProductSalesAggregateRow rowA = own.stream()
					.filter(row -> row.productId().equals(productA.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(rowA.soldQuantity()).isEqualTo(3L);
			assertThat(rowA.salesAmount()).isEqualByComparingTo(new BigDecimal("150000"));
			assertThat(rowA.orderCount()).isEqualTo(2L);

			ProductSalesAggregateRow rowB = own.stream()
					.filter(row -> row.productId().equals(productB.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(rowB.soldQuantity()).isEqualTo(3L);
			assertThat(rowB.salesAmount()).isEqualByComparingTo(new BigDecimal("30000"));
			assertThat(rowB.orderCount()).isEqualTo(1L);
		}

		private List<Long> sortedIds(Product productA, Product productB) {
			return productA.getId() < productB.getId()
					? List.of(productA.getId(), productB.getId())
					: List.of(productB.getId(), productA.getId());
		}

		@Test
		@DisplayName("취소된 주문은 집계에서 제외한다")
		void excludesCanceledOrder() {
			// given
			Product product = persistProduct("SAM Canceled Product", new BigDecimal("20000"));

			Order canceledOrder = OrderFixture.create(member, "20310710-SAMCANCEL001");
			canceledOrder.addItem(product, 5);
			em.persist(canceledOrder);
			em.persist(PaymentFixture.canceledAt(canceledOrder, "sam-cancel-key-1",
					LocalDateTime.of(2031, 7, 10, 10, 0), LocalDateTime.of(2031, 7, 10, 11, 0)));

			em.flush();
			em.clear();

			// when
			List<ProductSalesAggregateRow> result = salesAggregationQueryMapper.findProductSalesOf(SALE_DATE);

			// then
			assertThat(result).noneMatch(row -> row.productId().equals(product.getId()));
		}

		@Test
		@DisplayName("다른 날짜의 판매는 섞이지 않는다")
		void doesNotMixOtherDates() {
			// given
			Product product = persistProduct("SAM Other Date Product", new BigDecimal("15000"));

			Order otherDateOrder = OrderFixture.create(member, "20310711-SAMOTHER001");
			otherDateOrder.addItem(product, 1);
			OrderFixture.markPaid(otherDateOrder);
			em.persist(otherDateOrder);
			em.persist(PaymentFixture.approvedAt(otherDateOrder, "sam-other-key-1",
					LocalDateTime.of(2031, 7, 11, 10, 0)));

			em.flush();
			em.clear();

			// when
			List<ProductSalesAggregateRow> result = salesAggregationQueryMapper.findProductSalesOf(SALE_DATE);

			// then
			assertThat(result).noneMatch(row -> row.productId().equals(product.getId()));
		}
	}
}
