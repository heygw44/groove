package com.groove.admin.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.admin.dto.AdminStatsSummaryResponse;
import com.groove.admin.dto.DailySalesResponse;
import com.groove.admin.dto.LimitedDropStatsResponse;
import com.groove.admin.dto.PopularProductResponse;
import com.groove.admin.dto.PopularProductSortType;
import com.groove.admin.dto.PopularProductStatsCondition;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.LimitedPurchaseFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.entity.LimitedPurchase;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.payment.entity.Payment;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.support.MybatisTestSupport;

import jakarta.persistence.EntityManager;

/** 공유 테스트 DB 에 다른 테스트가 남긴 결제/주문/한정반이 섞이므로 이 테스트만 쓰는 먼 기간·자기 id 로만 단언한다. */
class AdminStatsMapperTest extends MybatisTestSupport {

	private static final LocalDateTime FAR_PERIOD_FROM = LocalDateTime.of(2031, 3, 1, 0, 0);
	private static final LocalDateTime FAR_PERIOD_TO_EXCLUSIVE = LocalDateTime.of(2031, 4, 1, 0, 0);

	@Autowired
	private AdminStatsMapper adminStatsMapper;

	@Autowired
	private EntityManager em;

	private Member member;
	private Artist artist;

	@BeforeEach
	void setUp() {
		member = MemberFixture.create("admin-stats-member@groove.com");
		artist = ArtistFixture.create("ASM Artist");
		em.persist(member);
		em.persist(artist);
	}

	private Order persistOrderWithPayment(String orderNumber, Product product, int quantity, String paymentKey,
			LocalDateTime approvedAt) {
		Order order = OrderFixture.create(member, orderNumber);
		order.addItem(product, quantity);
		OrderFixture.markPaid(order);
		em.persist(order);
		Payment payment = PaymentFixture.approvedAt(order, paymentKey, approvedAt);
		em.persist(payment);
		return order;
	}

	@Nested
	@DisplayName("findDailySales()")
	class FindDailySales {

		@Test
		@DisplayName("승인일에 매출을, 취소일에 취소액을 집계하고 기간 밖은 제외한다")
		void aggregatesByApprovedAndCanceledDate() {
			// given
			Product product = ProductFixture.create(artist, "ASM Daily Sales", new BigDecimal("30000"));
			em.persist(product);

			Order first = OrderFixture.create(member, "20310302-ASM00001");
			first.addItem(product, 1);
			OrderFixture.markPaid(first);
			em.persist(first);
			em.persist(PaymentFixture.approvedAt(first, "asm-daily-key-1",
					LocalDateTime.of(2031, 3, 2, 10, 0)));

			Order second = OrderFixture.create(member, "20310302-ASM00002");
			second.addItem(product, 1);
			OrderFixture.markPaid(second);
			em.persist(second);
			em.persist(PaymentFixture.approvedAt(second, "asm-daily-key-2",
					LocalDateTime.of(2031, 3, 2, 12, 0)));

			Order third = OrderFixture.create(member, "20310303-ASM00003");
			third.addItem(product, 1);
			OrderFixture.markPaid(third);
			em.persist(third);
			em.persist(PaymentFixture.canceledAt(third, "asm-daily-key-3",
					LocalDateTime.of(2031, 3, 3, 9, 0), LocalDateTime.of(2031, 3, 5, 15, 0)));

			Order outOfRange = OrderFixture.create(member, "20310410-ASM00004");
			outOfRange.addItem(product, 1);
			OrderFixture.markPaid(outOfRange);
			em.persist(outOfRange);
			em.persist(PaymentFixture.approvedAt(outOfRange, "asm-daily-key-4",
					LocalDateTime.of(2031, 4, 10, 0, 0)));

			em.flush();
			em.clear();

			// when
			List<DailySalesResponse> result = adminStatsMapper.findDailySales(FAR_PERIOD_FROM, FAR_PERIOD_TO_EXCLUSIVE);

			// then
			assertThat(result).extracting(DailySalesResponse::date)
					.doesNotContain(LocalDate.of(2031, 4, 10))
					.doesNotContain(LocalDate.of(2031, 3, 4));
			assertThat(result).isSortedAccordingTo((a, b) -> a.date().compareTo(b.date()));

			DailySalesResponse march02 = findByDate(result, LocalDate.of(2031, 3, 2));
			assertThat(march02.orderCount()).isEqualTo(2);
			assertThat(march02.salesAmount()).isEqualByComparingTo(new BigDecimal("60000"));
			assertThat(march02.cancelAmount()).isEqualByComparingTo(BigDecimal.ZERO);

			DailySalesResponse march03 = findByDate(result, LocalDate.of(2031, 3, 3));
			assertThat(march03.orderCount()).isEqualTo(1);
			assertThat(march03.salesAmount()).isEqualByComparingTo(new BigDecimal("30000"));
			assertThat(march03.cancelAmount()).isEqualByComparingTo(BigDecimal.ZERO);

			DailySalesResponse march05 = findByDate(result, LocalDate.of(2031, 3, 5));
			assertThat(march05.orderCount()).isZero();
			assertThat(march05.salesAmount()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(march05.cancelAmount()).isEqualByComparingTo(new BigDecimal("30000"));
		}

		private DailySalesResponse findByDate(List<DailySalesResponse> result, LocalDate date) {
			return result.stream()
					.filter(row -> row.date().equals(date))
					.findFirst()
					.orElseThrow();
		}
	}

	@Nested
	@DisplayName("findPopularProducts()")
	class FindPopularProducts {

		private Product productA;
		private Product productB;
		private Product productC;

		@BeforeEach
		void setUpProducts() {
			productA = ProductFixture.create(artist, "ASM Popular A", new BigDecimal("50000"));
			productB = ProductFixture.create(artist, "ASM Popular B", new BigDecimal("10000"));
			productC = ProductFixture.create(artist, "ASM Popular C", new BigDecimal("20000"));
			em.persist(productA);
			em.persist(productB);
			em.persist(productC);

			// PAID: A x 3
			Order paidOrder = OrderFixture.create(member, "20310310-ASMPOP001");
			paidOrder.addItem(productA, 3);
			OrderFixture.markPaid(paidOrder);
			em.persist(paidOrder);
			em.persist(PaymentFixture.approvedAt(paidOrder, "asm-pop-key-1", LocalDateTime.of(2031, 3, 10, 10, 0)));

			// DELIVERED: A x 1 + B x 5
			Order deliveredOrder = OrderFixture.create(member, "20310311-ASMPOP002");
			deliveredOrder.addItem(productA, 1);
			deliveredOrder.addItem(productB, 5);
			OrderFixture.markDelivered(deliveredOrder);
			em.persist(deliveredOrder);
			em.persist(PaymentFixture.approvedAt(deliveredOrder, "asm-pop-key-2",
					LocalDateTime.of(2031, 3, 11, 10, 0)));

			// PENDING: C x 10, no payment
			Order pendingOrder = OrderFixture.create(member, "20310312-ASMPOP003");
			pendingOrder.addItem(productC, 10);
			em.persist(pendingOrder);

			// CANCELED: B x 9, payment canceled
			Order canceledOrder = OrderFixture.create(member, "20310313-ASMPOP004");
			canceledOrder.addItem(productB, 9);
			em.persist(canceledOrder);
			em.persist(PaymentFixture.canceledAt(canceledOrder, "asm-pop-key-3",
					LocalDateTime.of(2031, 3, 13, 10, 0), LocalDateTime.of(2031, 3, 14, 10, 0)));

			em.flush();
			em.clear();
		}

		private List<Long> ownProductIds() {
			return List.of(productA.getId(), productB.getId(), productC.getId());
		}

		@Test
		@DisplayName("PAID·PREPARING·SHIPPED·DELIVERED 만 집계하고 quantity 정렬이면 판매 수량 내림차순으로 반환한다")
		void aggregatesPaidStatusesAndSortsByQuantity() {
			// given
			PopularProductStatsCondition condition = new PopularProductStatsCondition(FAR_PERIOD_FROM,
					FAR_PERIOD_TO_EXCLUSIVE, 100, PopularProductSortType.QUANTITY);

			// when
			List<PopularProductResponse> result = adminStatsMapper.findPopularProducts(condition);

			// then
			List<PopularProductResponse> own = result.stream()
					.filter(row -> ownProductIds().contains(row.productId()))
					.toList();
			assertThat(own).extracting(PopularProductResponse::productId)
					.containsExactly(productB.getId(), productA.getId());
			assertThat(own).filteredOn(row -> row.productId().equals(productB.getId()))
					.extracting(PopularProductResponse::soldQuantity)
					.containsExactly(5L);
			assertThat(own).filteredOn(row -> row.productId().equals(productA.getId()))
					.extracting(PopularProductResponse::soldQuantity)
					.containsExactly(4L);
			assertThat(result).extracting(PopularProductResponse::productId).doesNotContain(productC.getId());
		}

		@Test
		@DisplayName("sales 정렬이면 매출액 내림차순으로 반환한다")
		void sortsBySalesAmount() {
			// given
			PopularProductStatsCondition condition = new PopularProductStatsCondition(FAR_PERIOD_FROM,
					FAR_PERIOD_TO_EXCLUSIVE, 100, PopularProductSortType.SALES);

			// when
			List<PopularProductResponse> result = adminStatsMapper.findPopularProducts(condition);

			// then
			List<Long> ownOrder = result.stream()
					.map(PopularProductResponse::productId)
					.filter(ownProductIds()::contains)
					.toList();
			assertThat(ownOrder).containsExactly(productA.getId(), productB.getId());
		}

		@Test
		@DisplayName("limit 을 지정하면 그 개수만큼만 반환한다")
		void limitsResultCount() {
			// given
			PopularProductStatsCondition condition = new PopularProductStatsCondition(FAR_PERIOD_FROM,
					FAR_PERIOD_TO_EXCLUSIVE, 1, PopularProductSortType.QUANTITY);

			// when
			List<PopularProductResponse> result = adminStatsMapper.findPopularProducts(condition);

			// then
			assertThat(result).hasSize(1);
		}

		@Test
		@DisplayName("기간 밖이면 결과가 없다")
		void returnsEmptyWhenPeriodOutOfRange() {
			// given
			PopularProductStatsCondition condition = new PopularProductStatsCondition(
					LocalDateTime.of(2031, 5, 1, 0, 0), LocalDateTime.of(2031, 6, 1, 0, 0), 100,
					PopularProductSortType.QUANTITY);

			// when
			List<PopularProductResponse> result = adminStatsMapper.findPopularProducts(condition);

			// then
			assertThat(result).filteredOn(row -> ownProductIds().contains(row.productId())).isEmpty();
		}
	}

	@Nested
	@DisplayName("findLimitedDropStats()")
	class FindLimitedDropStats {

		@Test
		@DisplayName("SOLD_OUT 드롭은 마지막 구매 시각을 soldOutAt 으로, 판매율을 계산해 반환한다")
		void returnsSoldOutAtAndSellRateForSoldOutDrop() {
			// given
			Product product = ProductFixture.create(artist, "ASM Sold Out Drop", new BigDecimal("40000"));
			em.persist(product);
			LimitedDrop drop = LimitedDropFixture.open(product, 2);
			LimitedDropFixture.withOpenAt(drop, LocalDateTime.now().minusHours(1));
			LimitedDropFixture.withCloseAt(drop, LocalDateTime.now().plusHours(1));
			LimitedDropFixture.withSoldCount(drop, 2);
			LimitedDropFixture.withStatus(drop, LimitedDropStatus.SOLD_OUT);
			em.persist(drop);

			Member otherMember = MemberFixture.create("admin-stats-drop-member@groove.com");
			em.persist(otherMember);

			Order order1 = OrderFixture.create(member, "20310320-ASMDROP001");
			order1.addItem(product, 1);
			em.persist(order1);
			LimitedPurchase purchase1 = LimitedPurchaseFixture.create(drop, member, order1, 1);
			em.persist(purchase1);
			em.flush();

			Order order2 = OrderFixture.create(otherMember, "20310320-ASMDROP002");
			order2.addItem(product, 1);
			em.persist(order2);
			LimitedPurchase purchase2 = LimitedPurchaseFixture.create(drop, otherMember, order2, 1);
			em.persist(purchase2);
			em.flush();
			em.clear();

			LocalDateTime lastPurchaseCreatedAt = em.find(LimitedPurchase.class, purchase2.getId()).getCreatedAt();

			// when
			List<LimitedDropStatsResponse> result = adminStatsMapper.findLimitedDropStats();

			// then
			LimitedDropStatsResponse own = result.stream()
					.filter(row -> row.dropId().equals(drop.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(own.soldOutAt().truncatedTo(ChronoUnit.SECONDS))
					.isEqualTo(lastPurchaseCreatedAt.truncatedTo(ChronoUnit.SECONDS));
			assertThat(own.soldOutSeconds()).isPositive();
			assertThat(own.sellRate()).isEqualTo(100.0);
		}

		@Test
		@DisplayName("OPEN 드롭은 soldOutAt·soldOutSeconds 가 null 이고 판매율만 계산한다")
		void returnsNullSoldOutForOpenDrop() {
			// given
			Product product = ProductFixture.create(artist, "ASM Open Drop", new BigDecimal("40000"));
			em.persist(product);
			LimitedDrop drop = LimitedDropFixture.open(product, 4);
			LimitedDropFixture.withSoldCount(drop, 1);
			em.persist(drop);
			em.flush();
			em.clear();

			// when
			List<LimitedDropStatsResponse> result = adminStatsMapper.findLimitedDropStats();

			// then
			LimitedDropStatsResponse own = result.stream()
					.filter(row -> row.dropId().equals(drop.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(own.soldOutAt()).isNull();
			assertThat(own.soldOutSeconds()).isNull();
			assertThat(own.sellRate()).isEqualTo(25.0);
		}

		@Test
		@DisplayName("open_at 내림차순으로 정렬한다")
		void sortsByOpenAtDescending() {
			// given
			Product productOld = ProductFixture.create(artist, "ASM Order Old Drop", new BigDecimal("40000"));
			Product productNew = ProductFixture.create(artist, "ASM Order New Drop", new BigDecimal("40000"));
			em.persist(productOld);
			em.persist(productNew);

			LimitedDrop oldDrop = LimitedDropFixture.open(productOld, 10);
			LimitedDropFixture.withOpenAt(oldDrop, LocalDateTime.of(2031, 3, 1, 0, 0));
			em.persist(oldDrop);

			LimitedDrop newDrop = LimitedDropFixture.open(productNew, 10);
			LimitedDropFixture.withOpenAt(newDrop, LocalDateTime.of(2031, 3, 20, 0, 0));
			em.persist(newDrop);

			em.flush();
			em.clear();

			// when
			List<LimitedDropStatsResponse> result = adminStatsMapper.findLimitedDropStats();

			// then
			List<Long> ownOrder = result.stream()
					.map(LimitedDropStatsResponse::dropId)
					.filter(id -> id.equals(oldDrop.getId()) || id.equals(newDrop.getId()))
					.toList();
			assertThat(ownOrder).containsExactly(newDrop.getId(), oldDrop.getId());
		}
	}

	@Nested
	@DisplayName("findSummary()")
	class FindSummary {

		@Test
		@DisplayName("승인 결제·PENDING 주문을 today/tomorrow 경계로 집계한다")
		void aggregatesTodayApprovalsAndPendingOrders() {
			// given
			LocalDateTime todayStart = LocalDateTime.of(2031, 6, 10, 0, 0);
			LocalDateTime tomorrowStart = LocalDateTime.of(2031, 6, 11, 0, 0);
			Product product = ProductFixture.create(artist, "ASM Summary Product", new BigDecimal("25000"));
			em.persist(product);

			persistOrderWithPayment("20310610-ASMSUM001", product, 1, "asm-summary-key-1",
					LocalDateTime.of(2031, 6, 10, 9, 0));
			persistOrderWithPayment("20310610-ASMSUM002", product, 1, "asm-summary-key-2",
					LocalDateTime.of(2031, 6, 10, 15, 0));
			persistOrderWithPayment("20310611-ASMSUM003", product, 1, "asm-summary-key-3",
					LocalDateTime.of(2031, 6, 11, 9, 0));

			Order pendingOrder = OrderFixture.create(member, "20310610-ASMSUM004");
			pendingOrder.addItem(product, 1);
			em.persist(pendingOrder);

			em.flush();
			em.clear();

			// when
			AdminStatsSummaryResponse result = adminStatsMapper.findSummary(todayStart, tomorrowStart);

			// then
			assertThat(result.todaySalesAmount()).isEqualByComparingTo(new BigDecimal("50000"));
			assertThat(result.todayOrderCount()).isEqualTo(2);
			assertThat(result.todayNewMemberCount()).isZero();
			assertThat(result.pendingOrderCount()).isGreaterThanOrEqualTo(1);
		}
	}
}
