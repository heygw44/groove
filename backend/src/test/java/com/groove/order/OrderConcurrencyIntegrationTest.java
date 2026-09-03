package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

class OrderConcurrencyIntegrationTest extends IntegrationTestSupport {

	private static final int INITIAL_QUANTITY = 5;
	private static final int MEMBER_COUNT = 10;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private StockHistoryRepository stockHistoryRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderRepository orderRepository;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		executorService = Executors.newFixedThreadPool(MEMBER_COUNT);
	}

	@AfterEach
	void tearDown() {
		executorService.shutdownNow();
	}

	@Nested
	@DisplayName("동시에 같은 상품을 주문하면")
	class ConcurrentOrder {

		@Test
		@DisplayName("재고 수만큼만 성공하고 나머지는 STOCK_INSUFFICIENT 로 실패한다")
		void onlyStockQuantityOrdersSucceed() throws InterruptedException {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			Stock stock = stockRepository.saveAndFlush(StockFixture.create(product, INITIAL_QUANTITY));

			List<Long> memberIds = new ArrayList<>();
			List<Long> addressIds = new ArrayList<>();
			for (int i = 0; i < MEMBER_COUNT; i++) {
				Member member = memberRepository.save(
						MemberFixture.create("buyer-" + UUID.randomUUID() + "@groove.com"));
				Address address = addressRepository.save(AddressFixture.create(member));
				memberIds.add(member.getId());
				addressIds.add(address.getId());
			}

			CountDownLatch readyLatch = new CountDownLatch(MEMBER_COUNT);
			CountDownLatch startLatch = new CountDownLatch(1);
			List<AtomicReference<Throwable>> results = new ArrayList<>();
			AtomicInteger successCount = new AtomicInteger();

			// when
			for (int i = 0; i < MEMBER_COUNT; i++) {
				AtomicReference<Throwable> result = new AtomicReference<>();
				results.add(result);
				Long memberId = memberIds.get(i);
				Long addressId = addressIds.get(i);
				executorService.submit(() -> {
					try {
						readyLatch.countDown();
						startLatch.await();
						orderService.create(memberId, OrderFixture.directRequest(product.getId(), 1, addressId));
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
			long failureCount = results.stream()
					.map(AtomicReference::get)
					.filter(throwable -> throwable != null)
					.count();
			assertThat(successCount.get()).isEqualTo(INITIAL_QUANTITY);
			assertThat(failureCount).isEqualTo(MEMBER_COUNT - INITIAL_QUANTITY);
			results.stream()
					.map(AtomicReference::get)
					.filter(throwable -> throwable != null)
					.forEach(throwable -> assertThat(throwable)
							.isInstanceOf(BusinessException.class)
							.extracting("errorCode")
							.isEqualTo(ErrorCode.STOCK_INSUFFICIENT));

			Stock reloadedStock = stockRepository.findById(stock.getId()).orElseThrow();
			assertThat(reloadedStock.getQuantity()).isGreaterThanOrEqualTo(0);
			assertThat(reloadedStock.getQuantity()).isZero();
			Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
			assertThat(reloadedProduct.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
			long outHistoryCount = stockHistoryRepository.findAllByStockIdOrderByCreatedAtAsc(stock.getId()).stream()
					.filter(history -> history.getChangeType() == StockChangeType.OUT)
					.count();
			assertThat(outHistoryCount).isEqualTo(INITIAL_QUANTITY);
			long persistedOrderCount = orderRepository.findAll().stream()
					.filter(order -> memberIds.contains(order.getMember().getId()))
					.count();
			assertThat(persistedOrderCount).isEqualTo(INITIAL_QUANTITY);
		}
	}
}
