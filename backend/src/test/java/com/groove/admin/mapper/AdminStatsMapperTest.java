package com.groove.admin.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.admin.dto.AdminStatsSummaryResponse;
import com.groove.admin.dto.DailySalesResponse;
import com.groove.admin.dto.LimitedDropStatsRow;
import com.groove.admin.dto.PopularProductResponse;
import com.groove.admin.dto.PopularProductSortType;
import com.groove.admin.dto.PopularProductStatsCondition;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.entity.LimitedAttemptResult;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStat;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.payment.entity.Payment;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.stats.entity.SalesDaily;
import com.groove.stats.entity.SalesDailyProduct;
import com.groove.support.MybatisTestSupport;

import jakarta.persistence.EntityManager;

/** 공유 테스트 DB 에 다른 테스트가 남긴 결제/주문/한정반이 섞이므로 이 테스트만 쓰는 먼 기간·자기 id 로만 단언한다. */
class AdminStatsMapperTest extends MybatisTestSupport {

	private static final LocalDate FAR_PERIOD_FROM = LocalDate.of(2031, 3, 1);
	private static final LocalDate FAR_PERIOD_TO = LocalDate.of(2031, 3, 31);

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
		@DisplayName("기간 내 sales_daily 행을 sale_date 오름차순으로 반환하고 기간 밖은 제외한다")
		void returnsRowsInRangeOrderedByDate() {
			// given
			LocalDateTime aggregatedAt = LocalDateTime.of(2031, 3, 20, 3, 0);
			em.persist(SalesDaily.of(LocalDate.of(2031, 3, 2), 2, new BigDecimal("60000"), 0, BigDecimal.ZERO,
					aggregatedAt));
			em.persist(SalesDaily.of(LocalDate.of(2031, 3, 3), 1, new BigDecimal("30000"), 1,
					new BigDecimal("30000"), aggregatedAt));
			em.persist(SalesDaily.of(LocalDate.of(2031, 4, 10), 5, new BigDecimal("100000"), 0, BigDecimal.ZERO,
					aggregatedAt));
			em.flush();
			em.clear();

			// when
			List<DailySalesResponse> result = adminStatsMapper.findDailySales(FAR_PERIOD_FROM, FAR_PERIOD_TO);

			// then
			assertThat(result).extracting(DailySalesResponse::date)
					.containsExactly(LocalDate.of(2031, 3, 2), LocalDate.of(2031, 3, 3));

			DailySalesResponse march02 = findByDate(result, LocalDate.of(2031, 3, 2));
			assertThat(march02.orderCount()).isEqualTo(2);
			assertThat(march02.salesAmount()).isEqualByComparingTo(new BigDecimal("60000"));
			assertThat(march02.cancelAmount()).isEqualByComparingTo(BigDecimal.ZERO);

			DailySalesResponse march03 = findByDate(result, LocalDate.of(2031, 3, 3));
			assertThat(march03.orderCount()).isEqualTo(1);
			assertThat(march03.salesAmount()).isEqualByComparingTo(new BigDecimal("30000"));
			assertThat(march03.cancelAmount()).isEqualByComparingTo(new BigDecimal("30000"));
		}

		private DailySalesResponse findByDate(List<DailySalesResponse> result, LocalDate date) {
			return result.stream()
					.filter(row -> row.date().equals(date))
					.findFirst()
					.orElseThrow();
		}
	}

	@Nested
	@DisplayName("findAggregatedAt()")
	class FindAggregatedAt {

		@Test
		@DisplayName("기간 내 가장 오래된 aggregated_at 을 반환한다")
		void returnsOldestAggregatedAtInRange() {
			// given
			LocalDateTime older = LocalDateTime.of(2031, 3, 2, 3, 0);
			LocalDateTime newer = LocalDateTime.of(2031, 3, 3, 3, 30);
			em.persist(SalesDaily.of(LocalDate.of(2031, 3, 2), 1, new BigDecimal("10000"), 0, BigDecimal.ZERO,
					newer));
			em.persist(SalesDaily.of(LocalDate.of(2031, 3, 3), 1, new BigDecimal("10000"), 0, BigDecimal.ZERO,
					older));
			em.flush();
			em.clear();

			// when
			LocalDateTime result = adminStatsMapper.findAggregatedAt(FAR_PERIOD_FROM, FAR_PERIOD_TO);

			// then
			assertThat(result.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(older.truncatedTo(ChronoUnit.SECONDS));
		}

		@Test
		@DisplayName("기간 내 행이 없으면 null 을 반환한다")
		void returnsNullWhenNoRowInRange() {
			// when
			LocalDateTime result = adminStatsMapper.findAggregatedAt(LocalDate.of(2033, 1, 1),
					LocalDate.of(2033, 1, 31));

			// then
			assertThat(result).isNull();
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
			em.persist(productA.getAlbum());
			em.persist(productA);
			em.persist(productB.getAlbum());
			em.persist(productB);
			em.persist(productC.getAlbum());
			em.persist(productC);

			LocalDateTime aggregatedAt = LocalDateTime.of(2031, 3, 20, 3, 0);

			// A: 3(3/10) + 1(3/11) = 4개, 매출 200000
			em.persist(SalesDailyProduct.of(LocalDate.of(2031, 3, 10), productA, 3, new BigDecimal("150000"), 1,
					aggregatedAt));
			em.persist(SalesDailyProduct.of(LocalDate.of(2031, 3, 11), productA, 1, new BigDecimal("50000"), 1,
					aggregatedAt));

			// B: 5개(3/11), 매출 50000 - 수량은 더 많지만 매출은 A 보다 적다
			em.persist(SalesDailyProduct.of(LocalDate.of(2031, 3, 11), productB, 5, new BigDecimal("50000"), 1,
					aggregatedAt));

			// C: 기간 밖(4월) 이라 제외되어야 한다
			em.persist(SalesDailyProduct.of(LocalDate.of(2031, 4, 5), productC, 10, new BigDecimal("200000"), 1,
					aggregatedAt));

			em.flush();
			em.clear();
		}

		private List<Long> ownProductIds() {
			return List.of(productA.getId(), productB.getId(), productC.getId());
		}

		@Test
		@DisplayName("기간 내 날짜별 행을 상품 단위로 합산하고 quantity 정렬이면 판매 수량 내림차순으로 반환한다")
		void aggregatesAcrossDatesAndSortsByQuantity() {
			// given
			PopularProductStatsCondition condition = new PopularProductStatsCondition(FAR_PERIOD_FROM, FAR_PERIOD_TO,
					100, PopularProductSortType.QUANTITY);

			// when
			List<PopularProductResponse> result = adminStatsMapper.findPopularProducts(condition);

			// then
			List<PopularProductResponse> own = result.stream()
					.filter(row -> ownProductIds().contains(row.productId()))
					.toList();
			assertThat(own).extracting(PopularProductResponse::productId)
					.containsExactly(productB.getId(), productA.getId());
			assertThat(own).filteredOn(row -> row.productId().equals(productA.getId()))
					.extracting(PopularProductResponse::soldQuantity, PopularProductResponse::orderCount)
					.containsExactly(tuple(4L, 2L));
			assertThat(result).extracting(PopularProductResponse::productId).doesNotContain(productC.getId());
		}

		@Test
		@DisplayName("sales 정렬이면 매출액 내림차순으로 반환한다")
		void sortsBySalesAmount() {
			// given
			PopularProductStatsCondition condition = new PopularProductStatsCondition(FAR_PERIOD_FROM, FAR_PERIOD_TO,
					100, PopularProductSortType.SALES);

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
			PopularProductStatsCondition condition = new PopularProductStatsCondition(FAR_PERIOD_FROM, FAR_PERIOD_TO,
					1, PopularProductSortType.QUANTITY);

			// when
			List<PopularProductResponse> result = adminStatsMapper.findPopularProducts(condition);

			// then
			assertThat(result).hasSize(1);
		}

		@Test
		@DisplayName("기간 밖이면 결과가 없다")
		void returnsEmptyWhenPeriodOutOfRange() {
			// given
			PopularProductStatsCondition condition = new PopularProductStatsCondition(LocalDate.of(2031, 5, 1),
					LocalDate.of(2031, 5, 31), 100, PopularProductSortType.QUANTITY);

			// when
			List<PopularProductResponse> result = adminStatsMapper.findPopularProducts(condition);

			// then
			assertThat(result).filteredOn(row -> ownProductIds().contains(row.productId())).isEmpty();
		}
	}

	@Nested
	@DisplayName("findLimitedDropStats()")
	class FindLimitedDropStats {

		// 공유 DB 에 다른 테스트가 남긴 드롭이 섞이므로, 개별 필드 검증은 own id 필터로 하되
		// size 를 크게 잡아 own 드롭이 페이지 밖으로 밀려나지 않게 한다.
		private static final int LARGE_SIZE = 10_000;

		@Test
		@DisplayName("SOLD_OUT 드롭은 저장된 매진 시각으로 soldOutSeconds 를 계산하고 판매율도 계산해 반환한다")
		void returnsSoldOutAtAndSellRateForSoldOutDrop() {
			// given
			Product product = ProductFixture.create(artist, "ASM Sold Out Drop", new BigDecimal("40000"));
			em.persist(product.getAlbum());
			em.persist(product);
			LimitedDrop drop = LimitedDropFixture.open(product, 2);
			LimitedDropFixture.withOpenAt(drop, LocalDateTime.now().minusHours(1));
			LimitedDropFixture.withCloseAt(drop, LocalDateTime.now().plusHours(1));
			LimitedDropFixture.withSoldCount(drop, 2);
			LimitedDropFixture.withStatus(drop, LimitedDropStatus.SOLD_OUT);
			LocalDateTime soldOutAt = drop.getOpenAt().plusMinutes(30);
			LimitedDropFixture.withSoldOutAt(drop, soldOutAt);
			em.persist(drop);
			em.flush();
			em.clear();

			// when
			List<LimitedDropStatsRow> result = adminStatsMapper.findLimitedDropStats(0, LARGE_SIZE);

			// then
			LimitedDropStatsRow own = result.stream()
					.filter(row -> row.dropId().equals(drop.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(own.soldOutAt().truncatedTo(ChronoUnit.SECONDS))
					.isEqualTo(soldOutAt.truncatedTo(ChronoUnit.SECONDS));
			assertThat(own.soldOutSeconds()).isEqualTo(1800L);
			assertThat(own.sellRate()).isEqualTo(100.0);
		}

		@Test
		@DisplayName("매진 상태로 마감된 CLOSED 드롭도 저장된 매진 시각으로 soldOutSeconds 를 계산한다")
		void returnsSoldOutAtForClosedDropThatSoldOut() {
			// given
			Product product = ProductFixture.create(artist, "ASM Closed Sold Out Drop", new BigDecimal("40000"));
			em.persist(product.getAlbum());
			em.persist(product);
			LimitedDrop drop = LimitedDropFixture.open(product, 2);
			LimitedDropFixture.withOpenAt(drop, LocalDateTime.now().minusHours(2));
			LimitedDropFixture.withCloseAt(drop, LocalDateTime.now().minusHours(1));
			LimitedDropFixture.withSoldCount(drop, 2);
			LimitedDropFixture.withStatus(drop, LimitedDropStatus.CLOSED);
			LocalDateTime soldOutAt = drop.getOpenAt().plusMinutes(30);
			LimitedDropFixture.withSoldOutAt(drop, soldOutAt);
			em.persist(drop);
			em.flush();
			em.clear();

			// when
			List<LimitedDropStatsRow> result = adminStatsMapper.findLimitedDropStats(0, LARGE_SIZE);

			// then
			LimitedDropStatsRow own = result.stream()
					.filter(row -> row.dropId().equals(drop.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(own.soldOutAt().truncatedTo(ChronoUnit.SECONDS))
					.isEqualTo(soldOutAt.truncatedTo(ChronoUnit.SECONDS));
			assertThat(own.soldOutSeconds()).isEqualTo(1800L);
			assertThat(own.sellRate()).isEqualTo(100.0);
		}

		@Test
		@DisplayName("OPEN 드롭은 soldOutAt·soldOutSeconds 가 null 이고 판매율만 계산한다")
		void returnsNullSoldOutForOpenDrop() {
			// given
			Product product = ProductFixture.create(artist, "ASM Open Drop", new BigDecimal("40000"));
			em.persist(product.getAlbum());
			em.persist(product);
			LimitedDrop drop = LimitedDropFixture.open(product, 4);
			LimitedDropFixture.withSoldCount(drop, 1);
			em.persist(drop);
			em.flush();
			em.clear();

			// when
			List<LimitedDropStatsRow> result = adminStatsMapper.findLimitedDropStats(0, LARGE_SIZE);

			// then
			LimitedDropStatsRow own = result.stream()
					.filter(row -> row.dropId().equals(drop.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(own.soldOutAt()).isNull();
			assertThat(own.soldOutSeconds()).isNull();
			assertThat(own.sellRate()).isEqualTo(25.0);
		}

		@Test
		@DisplayName("limited_drop_stat 행이 없으면 실패 집계 4개 컬럼이 전부 null 이다")
		void returnsNullFailureCountsWhenNoStatRow() {
			// given
			Product product = ProductFixture.create(artist, "ASM No Stat Drop", new BigDecimal("40000"));
			em.persist(product.getAlbum());
			em.persist(product);
			LimitedDrop drop = LimitedDropFixture.open(product, 5);
			em.persist(drop);
			em.flush();
			em.clear();

			// when
			List<LimitedDropStatsRow> result = adminStatsMapper.findLimitedDropStats(0, LARGE_SIZE);

			// then
			LimitedDropStatsRow own = result.stream()
					.filter(row -> row.dropId().equals(drop.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(own.soldOutCount()).isNull();
			assertThat(own.alreadyPurchasedCount()).isNull();
			assertThat(own.notOpenCount()).isNull();
			assertThat(own.closedCount()).isNull();
		}

		@Test
		@DisplayName("limited_drop_stat 행이 있으면 LEFT JOIN 으로 실패 집계 4개 컬럼을 채운다")
		void returnsFailureCountsFromStatRow() {
			// given
			Product product = ProductFixture.create(artist, "ASM Stat Drop", new BigDecimal("40000"));
			em.persist(product.getAlbum());
			em.persist(product);
			LimitedDrop drop = LimitedDropFixture.open(product, 5);
			LimitedDropFixture.withStatus(drop, LimitedDropStatus.CLOSED);
			em.persist(drop);
			Map<LimitedAttemptResult, Long> counts = Map.of(
					LimitedAttemptResult.SOLD_OUT, 3L,
					LimitedAttemptResult.ALREADY_PURCHASED, 2L,
					LimitedAttemptResult.NOT_OPEN, 1L,
					LimitedAttemptResult.CLOSED, 4L);
			em.persist(LimitedDropStat.of(drop, counts, LocalDateTime.now()));
			em.flush();
			em.clear();

			// when
			List<LimitedDropStatsRow> result = adminStatsMapper.findLimitedDropStats(0, LARGE_SIZE);

			// then
			LimitedDropStatsRow own = result.stream()
					.filter(row -> row.dropId().equals(drop.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(own.soldOutCount()).isEqualTo(3L);
			assertThat(own.alreadyPurchasedCount()).isEqualTo(2L);
			assertThat(own.notOpenCount()).isEqualTo(1L);
			assertThat(own.closedCount()).isEqualTo(4L);
		}

		@Test
		@DisplayName("open_at 내림차순으로 정렬한다")
		void sortsByOpenAtDescending() {
			// given
			Product productOld = ProductFixture.create(artist, "ASM Order Old Drop", new BigDecimal("40000"));
			Product productNew = ProductFixture.create(artist, "ASM Order New Drop", new BigDecimal("40000"));
			em.persist(productOld.getAlbum());
			em.persist(productOld);
			em.persist(productNew.getAlbum());
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
			List<LimitedDropStatsRow> result = adminStatsMapper.findLimitedDropStats(0, LARGE_SIZE);

			// then
			List<Long> ownOrder = result.stream()
					.map(LimitedDropStatsRow::dropId)
					.filter(id -> id.equals(oldDrop.getId()) || id.equals(newDrop.getId()))
					.toList();
			assertThat(ownOrder).containsExactly(newDrop.getId(), oldDrop.getId());
		}

		@Test
		@DisplayName("size·offset 으로 페이지 경계를 나눈다")
		void splitsPagesByOffsetAndSize() {
			// given: 다른 테스트의 2031년대 픽스처보다도 뒤로 밀리지 않도록 그보다 먼 시각을 쓴다
			Product product1 = ProductFixture.create(artist, "ASM Page Drop 1", new BigDecimal("40000"));
			Product product2 = ProductFixture.create(artist, "ASM Page Drop 2", new BigDecimal("40000"));
			Product product3 = ProductFixture.create(artist, "ASM Page Drop 3", new BigDecimal("40000"));
			em.persist(product1.getAlbum());
			em.persist(product1);
			em.persist(product2.getAlbum());
			em.persist(product2);
			em.persist(product3.getAlbum());
			em.persist(product3);

			LimitedDrop drop1 = LimitedDropFixture.open(product1, 10);
			LimitedDropFixture.withOpenAt(drop1, LocalDateTime.of(9999, 12, 31, 23, 59, 59));
			em.persist(drop1);

			LimitedDrop drop2 = LimitedDropFixture.open(product2, 10);
			LimitedDropFixture.withOpenAt(drop2, LocalDateTime.of(9999, 12, 31, 23, 59, 58));
			em.persist(drop2);

			LimitedDrop drop3 = LimitedDropFixture.open(product3, 10);
			LimitedDropFixture.withOpenAt(drop3, LocalDateTime.of(9999, 12, 31, 23, 59, 57));
			em.persist(drop3);

			em.flush();
			em.clear();

			// when
			List<LimitedDropStatsRow> firstPage = adminStatsMapper.findLimitedDropStats(0, 2);
			List<LimitedDropStatsRow> secondPage = adminStatsMapper.findLimitedDropStats(2, 2);

			// then
			assertThat(firstPage).extracting(LimitedDropStatsRow::dropId)
					.containsExactly(drop1.getId(), drop2.getId());
			assertThat(secondPage.get(0).dropId()).isEqualTo(drop3.getId());
		}
	}

	@Nested
	@DisplayName("countLimitedDropStats()")
	class CountLimitedDropStats {

		@Test
		@DisplayName("전체 드롭 수는 findLimitedDropStats 전체 조회 결과 개수와 같다")
		void matchesFindResultSize() {
			// given: 필터가 없는 카운트라 own id 로 좁힐 수 없으므로, 같은 트랜잭션에서 조회한 findLimitedDropStats
			// 전체 결과 크기와 비교한다. count 를 두 번 호출하면 MyBatis 로컬 캐시가 두 번째 호출을 캐시된 값으로
			// 되돌려주므로(같은 세션 안에서 JPA 로 끼워 넣은 변경은 이 캐시를 못 지운다) 한 번만 호출한다.
			Product product = ProductFixture.create(artist, "ASM Count Drop", new BigDecimal("40000"));
			em.persist(product.getAlbum());
			em.persist(product);
			LimitedDrop drop = LimitedDropFixture.open(product, 5);
			em.persist(drop);
			em.flush();
			em.clear();

			// when
			long count = adminStatsMapper.countLimitedDropStats();
			List<LimitedDropStatsRow> all = adminStatsMapper.findLimitedDropStats(0, (int) count);

			// then
			assertThat(all).hasSize((int) count);
			assertThat(all).extracting(LimitedDropStatsRow::dropId).contains(drop.getId());
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
			em.persist(product.getAlbum());
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

		@Test
		@DisplayName("당일 승인된 결제가 없으면 매출과 건수를 0으로 채운 1행을 반환한다")
		void returnsZeroRowWhenNoPaymentApprovedToday() {
			// given
			LocalDateTime todayStart = LocalDateTime.of(2032, 1, 1, 0, 0);
			LocalDateTime tomorrowStart = LocalDateTime.of(2032, 1, 2, 0, 0);

			// when
			AdminStatsSummaryResponse result = adminStatsMapper.findSummary(todayStart, tomorrowStart);

			// then
			assertThat(result).isNotNull();
			assertThat(result.todaySalesAmount()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(result.todayOrderCount()).isZero();
		}

		@Test
		@DisplayName("취소된 결제도 승인일 기준으로 오늘 매출에 포함한다")
		void countsCanceledPaymentAmountInTodaySales() {
			// given
			LocalDateTime todayStart = LocalDateTime.of(2032, 2, 1, 0, 0);
			LocalDateTime tomorrowStart = LocalDateTime.of(2032, 2, 2, 0, 0);
			Product product = ProductFixture.create(artist, "ASM Summary Canceled Product", new BigDecimal("15000"));
			em.persist(product.getAlbum());
			em.persist(product);

			Order order = OrderFixture.create(member, "20320201-ASMSUM005");
			order.addItem(product, 1);
			OrderFixture.markPaid(order);
			em.persist(order);
			em.persist(PaymentFixture.canceledAt(order, "asm-summary-key-5",
					LocalDateTime.of(2032, 2, 1, 9, 0), LocalDateTime.of(2032, 2, 1, 12, 0)));

			em.flush();
			em.clear();

			// when
			AdminStatsSummaryResponse result = adminStatsMapper.findSummary(todayStart, tomorrowStart);

			// then
			assertThat(result.todaySalesAmount()).isEqualByComparingTo(new BigDecimal("15000"));
			assertThat(result.todayOrderCount()).isEqualTo(1);
		}
	}
}
