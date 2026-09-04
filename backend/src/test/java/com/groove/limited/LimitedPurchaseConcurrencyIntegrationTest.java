package com.groove.limited;

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
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedPurchaseService;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

class LimitedPurchaseConcurrencyIntegrationTest extends IntegrationTestSupport {

	private static final int TOTAL_QUANTITY = 5;
	private static final int MEMBER_COUNT = 20;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private LimitedDropRepository limitedDropRepository;

	@Autowired
	private LimitedPurchaseRepository limitedPurchaseRepository;

	@Autowired
	private LimitedDropRedisService limitedDropRedisService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private LimitedPurchaseService limitedPurchaseService;

	private Long dropId;

	private static LimitedDrop openDrop(Product product, int totalQuantity) {
		LimitedDrop drop = LimitedDropFixture.open(product, totalQuantity);
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
		LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
		return drop;
	}

	@AfterEach
	void tearDown() {
		limitedDropRedisService.clear(dropId);
	}

	@Nested
	@DisplayName("동시에 같은 한정반을 구매하면")
	class ConcurrentPurchase {

		@Test
		@DisplayName("재고 수만큼만 성공하고 초과 판매 없이 소진된다")
		void onlyStockQuantitySucceedsWithoutOversell() throws InterruptedException {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			stockRepository.saveAndFlush(StockFixture.create(product, TOTAL_QUANTITY));
			LimitedDrop drop = limitedDropRepository.saveAndFlush(openDrop(product, TOTAL_QUANTITY));
			dropId = drop.getId();
			// create-drop 로 PK 가 재사용될 수 있어, 다른 테스트가 남긴 낡은 키를 먼저 지우고 초기화한다.
			limitedDropRedisService.clear(dropId);
			limitedDropRedisService.initStock(dropId, TOTAL_QUANTITY);

			List<Long> memberIds = new ArrayList<>();
			List<Long> addressIds = new ArrayList<>();
			for (int i = 0; i < MEMBER_COUNT; i++) {
				Member member = memberRepository.save(
						MemberFixture.create("buyer-" + UUID.randomUUID() + "@groove.com"));
				Address address = addressRepository.save(AddressFixture.create(member));
				memberIds.add(member.getId());
				addressIds.add(address.getId());
			}

			ExecutorService executorService = Executors.newFixedThreadPool(MEMBER_COUNT);
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
						limitedPurchaseService.purchase(dropId, memberId, addressId);
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
			assertThat(successCount.get()).isEqualTo(TOTAL_QUANTITY);
			results.stream()
					.map(AtomicReference::get)
					.filter(throwable -> throwable != null)
					.forEach(throwable -> assertThat(throwable)
							.isInstanceOf(BusinessException.class)
							.extracting("errorCode")
							.isIn(ErrorCode.LIMITED_SOLD_OUT, ErrorCode.LIMITED_ALREADY_PURCHASED));

			Stock reloadedStock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(reloadedStock.getQuantity()).isZero();
			LimitedDrop reloadedDrop = limitedDropRepository.findById(dropId).orElseThrow();
			assertThat(reloadedDrop.getSoldCount()).isEqualTo(TOTAL_QUANTITY);
			long persistedOrderCount = orderRepository.findAll().stream()
					.filter(order -> memberIds.contains(order.getMember().getId()))
					.count();
			assertThat(persistedOrderCount).isEqualTo(TOTAL_QUANTITY);
		}

		@Test
		@DisplayName("같은 회원이 동시에 여러 번 요청해도 1건만 성공한다")
		void onlyOneSucceedsForSameMemberConcurrentRequests() throws InterruptedException {
			// given
			int requestCount = 10;
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			stockRepository.saveAndFlush(StockFixture.create(product, TOTAL_QUANTITY));
			LimitedDrop drop = limitedDropRepository.saveAndFlush(openDrop(product, TOTAL_QUANTITY));
			dropId = drop.getId();
			// create-drop 로 PK 가 재사용될 수 있어, 다른 테스트가 남긴 낡은 키를 먼저 지우고 초기화한다.
			limitedDropRedisService.clear(dropId);
			limitedDropRedisService.initStock(dropId, TOTAL_QUANTITY);

			Member member = memberRepository.save(MemberFixture.create("dup-" + UUID.randomUUID() + "@groove.com"));
			Address address = addressRepository.save(AddressFixture.create(member));

			ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
			CountDownLatch readyLatch = new CountDownLatch(requestCount);
			CountDownLatch startLatch = new CountDownLatch(1);
			AtomicInteger successCount = new AtomicInteger();
			List<AtomicReference<Throwable>> results = new ArrayList<>();

			// when
			for (int i = 0; i < requestCount; i++) {
				AtomicReference<Throwable> result = new AtomicReference<>();
				results.add(result);
				executorService.submit(() -> {
					try {
						readyLatch.countDown();
						startLatch.await();
						limitedPurchaseService.purchase(dropId, member.getId(), address.getId());
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
			assertThat(limitedPurchaseRepository.existsByDropIdAndMemberId(dropId, member.getId())).isTrue();
		}
	}
}
