package com.groove.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.coupon.repository.CouponRepository;
import com.groove.coupon.repository.MemberCouponRepository;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

class MemberCouponUseConcurrencyIntegrationTest extends IntegrationTestSupport {

	private static final int INITIAL_QUANTITY = 10;
	private static final int ORDER_COUNT = 2;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private CouponRepository couponRepository;

	@Autowired
	private MemberCouponRepository memberCouponRepository;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderRepository orderRepository;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		executorService = Executors.newFixedThreadPool(ORDER_COUNT);
	}

	@AfterEach
	void tearDown() {
		executorService.shutdownNow();
	}

	@Nested
	@DisplayName("같은 쿠폰으로 동시에 두 건을 주문하면")
	class ConcurrentUse {

		@Test
		@DisplayName("1건만 성공하고 나머지는 COUPON_ALREADY_USED 로 실패한다")
		void onlyOneOrderConsumesCoupon() throws InterruptedException {
			// given: 재고 부족이 실패 원인으로 섞이지 않게 재고를 넉넉히 둔다.
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			Stock stock = stockRepository.saveAndFlush(StockFixture.create(product, INITIAL_QUANTITY));

			Member member = memberRepository.save(
					MemberFixture.create("coupon-user-" + UUID.randomUUID() + "@groove.com"));
			Address address = addressRepository.save(AddressFixture.create(member));
			String code = "USE" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
			Coupon coupon = couponRepository.save(CouponFixture.fixed(code, BigDecimal.valueOf(1000)));
			MemberCoupon memberCoupon = memberCouponRepository.save(MemberCouponFixture.create(member, coupon));

			Long memberId = member.getId();
			Long addressId = address.getId();
			Long memberCouponId = memberCoupon.getId();

			CountDownLatch readyLatch = new CountDownLatch(ORDER_COUNT);
			CountDownLatch startLatch = new CountDownLatch(1);
			List<AtomicReference<Throwable>> results = new ArrayList<>();
			AtomicInteger successCount = new AtomicInteger();

			// when
			for (int i = 0; i < ORDER_COUNT; i++) {
				AtomicReference<Throwable> result = new AtomicReference<>();
				results.add(result);
				executorService.submit(() -> {
					try {
						readyLatch.countDown();
						startLatch.await();
						orderService.create(memberId, OrderFixture.directRequestWithCoupon(product.getId(), 1,
								addressId, memberCouponId));
						successCount.incrementAndGet();
					} catch (Throwable throwable) {
						result.set(throwable);
					}
				});
			}
			readyLatch.await();
			startLatch.countDown();
			executorService.shutdown();
			boolean finished = executorService.awaitTermination(30, TimeUnit.SECONDS);

			// then
			assertThat(finished).isTrue();
			assertThat(successCount.get()).isEqualTo(1);
			List<Throwable> failures = results.stream()
					.map(AtomicReference::get)
					.filter(throwable -> throwable != null)
					.toList();
			assertThat(failures).hasSize(ORDER_COUNT - 1);
			failures.forEach(throwable -> assertThat(throwable)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_ALREADY_USED));

			List<Order> persistedOrders = orderRepository.findAll().stream()
					.filter(order -> order.getMember().getId().equals(memberId))
					.toList();
			assertThat(persistedOrders).hasSize(1);
			MemberCoupon reloadedMemberCoupon = memberCouponRepository.findById(memberCouponId).orElseThrow();
			assertThat(reloadedMemberCoupon.isUsed()).isTrue();
			assertThat(reloadedMemberCoupon.getUsedOrderId()).isEqualTo(persistedOrders.get(0).getId());
			Stock reloadedStock = stockRepository.findById(stock.getId()).orElseThrow();
			assertThat(reloadedStock.getQuantity()).isEqualTo(INITIAL_QUANTITY - 1);
		}
	}
}
