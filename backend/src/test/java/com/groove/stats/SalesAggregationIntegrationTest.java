package com.groove.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
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
import com.groove.fixture.StockFixture;
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
import com.groove.stats.entity.SalesDaily;
import com.groove.stats.entity.SalesDailyProduct;
import com.groove.stats.entity.SalesDailyProductId;
import com.groove.stats.repository.SalesDailyProductRepository;
import com.groove.stats.repository.SalesDailyRepository;
import com.groove.stats.service.SalesAggregationService;
import com.groove.support.IntegrationTestSupport;

class SalesAggregationIntegrationTest extends IntegrationTestSupport {

	// 공유 테스트 DB 오염을 피하려고 먼 미래 날짜를 쓴다.
	private static final LocalDate SALE_DATE = LocalDate.of(2031, 6, 15);

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
	private SalesDailyProductRepository salesDailyProductRepository;

	@Autowired
	private SalesAggregationService salesAggregationService;

	@Autowired
	private Clock clock;

	private Member createMember() {
		return memberRepository.save(MemberFixture.create("buyer-" + UUID.randomUUID() + "@groove.com"));
	}

	private Product createProduct() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		return productRepository.save(createdProduct);
	}

	private Order createPaidOrder(Member member, Product product, int quantity, String orderNumber) {
		Order order = OrderFixture.create(member, orderNumber);
		order.addItem(product, quantity);
		OrderFixture.markPaid(order);
		return orderRepository.saveAndFlush(order);
	}

	private Payment approvePaymentAt(Order order, String paymentKey, LocalDateTime approvedAt) {
		Payment payment = PaymentFixture.approvedAt(order, paymentKey, approvedAt);
		return paymentRepository.saveAndFlush(payment);
	}

	private LocalDateTime saleDateTime() {
		return SALE_DATE.atTime(10, 0);
	}

	@Nested
	@DisplayName("aggregateDate()")
	class AggregateDate {

		@Test
		@DisplayName("결제 승인 뒤 집계하면 매출이 잡히고, 주문을 취소하고 재집계하면 상품 판매 행이 사라진다")
		void productRowDisappearsAfterFullCancellation() {
			// given
			Member member = createMember();
			Product product = createProduct();
			Order order = createPaidOrder(member, product, 2, "SA" + UUID.randomUUID().toString().substring(0, 10));
			approvePaymentAt(order, "sa-key-" + order.getId(), saleDateTime());

			// when
			salesAggregationService.aggregateDate(SALE_DATE);

			// then
			SalesDailyProductId productId = SalesDailyProductId.of(SALE_DATE, product.getId());
			Optional<SalesDailyProduct> beforeCancel = salesDailyProductRepository.findById(productId);
			assertThat(beforeCancel).isPresent();
			assertThat(beforeCancel.get().getSoldQuantity()).isEqualTo(2);

			SalesDaily beforeCancelDaily = salesDailyRepository.findById(SALE_DATE).orElseThrow();
			assertThat(beforeCancelDaily.getOrderCount()).isEqualTo(1);

			// when — 주문을 취소하고 같은 날짜를 재집계한다
			order.cancel("테스트 취소");
			orderRepository.saveAndFlush(order);
			salesAggregationService.aggregateDate(SALE_DATE);

			// then — 원본 GROUP BY 에서 이 상품이 완전히 사라져 사전 집계 행도 사라진다
			assertThat(salesDailyProductRepository.findById(productId)).isEmpty();
		}

		@Test
		@DisplayName("두 주문 중 하나만 취소하고 재집계하면 판매 수량이 줄어든다")
		void productSalesDecreaseAfterPartialCancellation() {
			// given
			Member member = createMember();
			Product product = createProduct();
			Order order1 = createPaidOrder(member, product, 2, "SA" + UUID.randomUUID().toString().substring(0, 10));
			Order order2 = createPaidOrder(member, product, 3, "SA" + UUID.randomUUID().toString().substring(0, 10));
			approvePaymentAt(order1, "sa-key-" + order1.getId(), saleDateTime());
			approvePaymentAt(order2, "sa-key-" + order2.getId(), saleDateTime());

			salesAggregationService.aggregateDate(SALE_DATE);
			SalesDailyProductId productId = SalesDailyProductId.of(SALE_DATE, product.getId());
			assertThat(salesDailyProductRepository.findById(productId).orElseThrow().getSoldQuantity()).isEqualTo(5);

			// when — 3개짜리 주문만 취소하고 재집계한다
			order2.cancel("테스트 취소");
			orderRepository.saveAndFlush(order2);
			salesAggregationService.aggregateDate(SALE_DATE);

			// then — 남은 주문분(2개)만큼으로 줄어든다
			SalesDailyProduct reaggregated = salesDailyProductRepository.findById(productId).orElseThrow();
			assertThat(reaggregated.getSoldQuantity()).isEqualTo(2);
		}

		@Test
		@DisplayName("결제가 하나도 없는 날을 집계하면 sales_daily 가 0 행으로 남는다")
		void leavesZeroRowWhenNoPaymentsOnDate() {
			// given — 이 테스트 전용 날짜라 원본에 아무 결제도 없다
			LocalDate emptyDate = LocalDate.of(2031, 6, 16);

			// when
			salesAggregationService.aggregateDate(emptyDate);

			// then
			SalesDaily salesDaily = salesDailyRepository.findById(emptyDate).orElseThrow();
			assertThat(salesDaily.getOrderCount()).isZero();
			assertThat(salesDaily.getSalesAmount()).isEqualByComparingTo(BigDecimal.ZERO);
		}

		@Test
		@DisplayName("같은 날짜를 두 번 집계해도 값이 같다")
		void isIdempotentWhenAggregatedTwice() {
			// given
			Member member = createMember();
			Product product = createProduct();
			Order order = createPaidOrder(member, product, 1, "SA" + UUID.randomUUID().toString().substring(0, 10));
			approvePaymentAt(order, "sa-key-" + order.getId(), saleDateTime());

			// when
			salesAggregationService.aggregateDate(SALE_DATE);
			SalesDaily first = salesDailyRepository.findById(SALE_DATE).orElseThrow();
			SalesDailyProductId productId = SalesDailyProductId.of(SALE_DATE, product.getId());
			SalesDailyProduct firstProduct = salesDailyProductRepository.findById(productId).orElseThrow();

			salesAggregationService.aggregateDate(SALE_DATE);
			SalesDaily second = salesDailyRepository.findById(SALE_DATE).orElseThrow();
			SalesDailyProduct secondProduct = salesDailyProductRepository.findById(productId).orElseThrow();

			// then
			assertThat(second.getOrderCount()).isEqualTo(first.getOrderCount());
			assertThat(second.getSalesAmount()).isEqualByComparingTo(first.getSalesAmount());
			assertThat(secondProduct.getSoldQuantity()).isEqualTo(firstProduct.getSoldQuantity());
		}
	}
}
