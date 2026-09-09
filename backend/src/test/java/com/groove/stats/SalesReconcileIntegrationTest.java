package com.groove.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.Payment;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.stats.entity.ReconcileMetric;
import com.groove.stats.entity.SalesDaily;
import com.groove.stats.entity.SalesReconcileLog;
import com.groove.stats.repository.SalesDailyRepository;
import com.groove.stats.repository.SalesReconcileLogRepository;
import com.groove.stats.service.ReconcileOutcome;
import com.groove.stats.service.SalesAggregationService;
import com.groove.stats.service.SalesReconcileService;
import com.groove.support.IntegrationTestSupport;

class SalesReconcileIntegrationTest extends IntegrationTestSupport {

	// 공유 테스트 DB 오염을 피하려고 먼 미래 날짜를 쓴다.
	private static final LocalDate SALE_DATE = LocalDate.of(2031, 7, 20);

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private SalesDailyRepository salesDailyRepository;

	@Autowired
	private SalesReconcileLogRepository salesReconcileLogRepository;

	@Autowired
	private SalesAggregationService salesAggregationService;

	@Autowired
	private SalesReconcileService salesReconcileService;

	@Autowired
	private Clock clock;

	private void createPaidOrderAndPayment() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = ProductFixture.create(artist);
		albumRepository.save(product.getAlbum());
		product = productRepository.save(product);

		Member member = memberRepository.save(MemberFixture.create("buyer-" + UUID.randomUUID() + "@groove.com"));
		Order order = OrderFixture.create(member, "SR" + UUID.randomUUID().toString().substring(0, 10));
		order.addItem(product, 2);
		OrderFixture.markPaid(order);
		order = orderRepository.saveAndFlush(order);

		Payment payment = PaymentFixture.approvedAt(order, "sr-key-" + order.getId(), SALE_DATE.atTime(10, 0));
		paymentRepository.saveAndFlush(payment);
	}

	@Nested
	@DisplayName("reconcileDate()")
	class ReconcileDate {

		@Test
		@DisplayName("집계 테이블 값을 일부러 틀리게 만든 뒤 대사하면 로그가 기록되고 재계산으로 자동 복구된다")
		void detectsAndRepairsCorruptedAggregate() {
			// given — 정상 집계를 만든 뒤 sales_daily 값을 일부러 틀리게 덮어쓴다
			createPaidOrderAndPayment();
			salesAggregationService.aggregateDate(SALE_DATE);

			SalesDaily corrupted = salesDailyRepository.findById(SALE_DATE).orElseThrow();
			corrupted.replace(corrupted.getOrderCount() + 1, corrupted.getSalesAmount(), corrupted.getCancelCount(),
					corrupted.getCancelAmount(), LocalDateTime.now(clock));
			salesDailyRepository.saveAndFlush(corrupted);

			// when
			ReconcileOutcome outcome = salesReconcileService.reconcileDate(SALE_DATE);

			// then — 불일치가 기록되고, 재계산 1회로 원본 값과 다시 일치해 로그가 repaired=true 로 닫힌다
			assertThat(outcome.mismatchCount()).isGreaterThan(0);
			assertThat(outcome.hasUnresolvedCritical()).isFalse();

			List<SalesReconcileLog> logs = salesReconcileLogRepository.findAll().stream()
					.filter(log -> log.getSaleDate().equals(SALE_DATE))
					.filter(log -> log.getMetric() == ReconcileMetric.DAILY_ORDER_COUNT)
					.toList();
			assertThat(logs).hasSize(1);
			assertThat(logs.get(0).isRepaired()).isTrue();

			SalesDaily repaired = salesDailyRepository.findById(SALE_DATE).orElseThrow();
			assertThat(repaired.getOrderCount()).isEqualTo(1L);
		}

		@Test
		@DisplayName("원본과 집계가 일치하면 아무 로그도 남기지 않는다")
		void recordsNothingWhenAlreadyConsistent() {
			// given
			LocalDate cleanDate = SALE_DATE.plusDays(1);
			salesAggregationService.aggregateDate(cleanDate);

			// when
			ReconcileOutcome outcome = salesReconcileService.reconcileDate(cleanDate);

			// then
			assertThat(outcome.mismatchCount()).isZero();
			List<SalesReconcileLog> logs = salesReconcileLogRepository.findAll().stream()
					.filter(log -> log.getSaleDate().equals(cleanDate))
					.toList();
			assertThat(logs).isEmpty();
		}
	}
}
