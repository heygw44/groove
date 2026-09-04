package com.groove.limited;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.LimitedPurchaseFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedPurchaseService;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.scheduler.OrderExpirationScheduler;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

class LimitedPurchaseConcurrencyIntegrationTest extends IntegrationTestSupport {

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

	@Autowired
	private OrderExpirationScheduler orderExpirationScheduler;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private Clock clock;

	private Long dropId;
	private Long productId;

	@AfterEach
	void tearDown() {
		limitedDropRedisService.clear(dropId);
	}

	private Long prepareOpenDrop(int totalQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist));
		stockRepository.saveAndFlush(StockFixture.create(product, totalQuantity));

		LimitedDrop drop = LimitedDropFixture.open(product, totalQuantity);
		LocalDateTime now = LocalDateTime.now(clock);
		LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
		LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
		limitedDropRepository.saveAndFlush(drop);

		dropId = drop.getId();
		productId = product.getId();
		// create-drop 로 PK 가 재사용될 수 있어, 다른 테스트가 남긴 낡은 키를 먼저 지우고 초기화한다.
		limitedDropRedisService.clear(dropId);
		limitedDropRedisService.initStock(dropId, totalQuantity);
		return dropId;
	}

	private List<Buyer> createBuyers(int count) {
		List<Buyer> buyers = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Member member = memberRepository.save(MemberFixture.create("buyer-" + UUID.randomUUID() + "@groove.com"));
			Address address = addressRepository.save(AddressFixture.create(member));
			buyers.add(new Buyer(member.getId(), address.getId()));
		}
		return buyers;
	}

	private ConcurrencyResult runConcurrently(int threads, IntFunction<Runnable> taskFactory)
			throws InterruptedException {
		ExecutorService executorService = Executors.newFixedThreadPool(threads);
		CountDownLatch readyLatch = new CountDownLatch(threads);
		CountDownLatch startLatch = new CountDownLatch(1);
		AtomicInteger successCount = new AtomicInteger();
		List<Throwable> failures = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			Runnable task = taskFactory.apply(i);
			executorService.submit(() -> {
				try {
					readyLatch.countDown();
					startLatch.await();
					task.run();
					successCount.incrementAndGet();
				} catch (Throwable throwable) {
					synchronized (failures) {
						failures.add(throwable);
					}
				}
			});
		}
		readyLatch.await();
		startLatch.countDown();
		executorService.shutdown();
		boolean finished = executorService.awaitTermination(60, TimeUnit.SECONDS);

		return new ConcurrencyResult(finished, successCount.get(), failures);
	}

	private String stockKey() {
		return LimitedDropRedisService.STOCK_KEY_PREFIX + dropId;
	}

	private String buyersKey() {
		return LimitedDropRedisService.BUYERS_KEY_PREFIX + dropId;
	}

	private record Buyer(Long memberId, Long addressId) {
	}

	private record ConcurrencyResult(boolean finished, int successCount, List<Throwable> failures) {
	}

	@Nested
	@DisplayName("purchase()")
	class Purchase {

		private static final int TOTAL_QUANTITY = 50;
		private static final int MEMBER_COUNT = 200;
		private static final int SAME_MEMBER_REQUEST_COUNT = 20;

		@Test
		@DisplayName("여러 회원이 동시에 구매하면 재고 수만큼만 성공하고 초과 판매 없이 소진된다")
		void sellsExactlyStockQuantityAmongManyMembers() throws InterruptedException {
			// given
			prepareOpenDrop(TOTAL_QUANTITY);
			List<Buyer> buyers = createBuyers(MEMBER_COUNT);

			// when
			ConcurrencyResult result = runConcurrently(MEMBER_COUNT, i -> {
				Buyer buyer = buyers.get(i);
				return () -> limitedPurchaseService.purchase(dropId, buyer.memberId(), buyer.addressId());
			});

			// then
			assertThat(result.finished()).isTrue();
			assertThat(result.successCount()).isEqualTo(TOTAL_QUANTITY);
			result.failures().forEach(throwable -> assertThat(throwable)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isIn(ErrorCode.LIMITED_SOLD_OUT, ErrorCode.LIMITED_ALREADY_PURCHASED));

			assertThat(limitedPurchaseRepository.countByDropId(dropId)).isEqualTo(TOTAL_QUANTITY);
			Stock reloadedStock = stockRepository.findByProductId(productId).orElseThrow();
			assertThat(reloadedStock.getQuantity()).isZero();
			LimitedDrop reloadedDrop = limitedDropRepository.findById(dropId).orElseThrow();
			assertThat(reloadedDrop.getSoldCount()).isEqualTo(TOTAL_QUANTITY);
			assertThat(reloadedDrop.getStatus()).isEqualTo(LimitedDropStatus.SOLD_OUT);
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("0");
			assertThat(redisTemplate.opsForSet().size(buyersKey())).isEqualTo((long) TOTAL_QUANTITY);

			List<Long> memberIds = buyers.stream().map(Buyer::memberId).toList();
			long persistedOrderCount = orderRepository.findAll().stream()
					.filter(order -> memberIds.contains(order.getMember().getId()))
					.count();
			assertThat(persistedOrderCount).isEqualTo(TOTAL_QUANTITY);
		}

		@Test
		@DisplayName("같은 회원이 동시에 여러 번 요청해도 1건만 성공한다")
		void allowsOnlyOnePurchasePerMember() throws InterruptedException {
			// given
			prepareOpenDrop(TOTAL_QUANTITY);
			Buyer buyer = createBuyers(1).get(0);

			// when
			ConcurrencyResult result = runConcurrently(SAME_MEMBER_REQUEST_COUNT,
					i -> () -> limitedPurchaseService.purchase(dropId, buyer.memberId(), buyer.addressId()));

			// then
			assertThat(result.finished()).isTrue();
			assertThat(result.successCount()).isEqualTo(1);
			assertThat(result.failures()).hasSize(SAME_MEMBER_REQUEST_COUNT - 1);
			// Lua 가 재고보다 구매자 등록(SISMEMBER) 을 먼저 확인하므로 SOLD_OUT 은 절대 섞이지 않는다.
			result.failures().forEach(throwable -> assertThat(throwable)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_ALREADY_PURCHASED));

			assertThat(limitedPurchaseRepository.existsByDropIdAndMemberId(dropId, buyer.memberId())).isTrue();
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo(String.valueOf(TOTAL_QUANTITY - 1));
			assertThat(redisTemplate.opsForSet().size(buyersKey())).isEqualTo(1L);
		}

		@Test
		@DisplayName("DB 단계에서 실패하면 Redis 선점을 되돌려 재구매가 가능해진다")
		void releasesRedisReservationWhenDbStageFails() {
			// given
			int totalQuantity = 5;
			prepareOpenDrop(totalQuantity);
			List<Buyer> buyers = createBuyers(2);
			Buyer buyerA = buyers.get(0);
			Buyer buyerB = buyers.get(1);

			// when & then
			assertThatThrownBy(() -> limitedPurchaseService.purchase(dropId, buyerA.memberId(), buyerB.addressId()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_ADDRESS_NOT_FOUND);

			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo(String.valueOf(totalQuantity));
			assertThat(redisTemplate.opsForSet().isMember(buyersKey(), String.valueOf(buyerA.memberId()))).isFalse();
			assertThat(limitedPurchaseRepository.countByDropId(dropId)).isZero();

			LimitedPurchaseResponse response = limitedPurchaseService.purchase(dropId, buyerA.memberId(),
					buyerA.addressId());

			assertThat(response.orderId()).isNotNull();
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo(String.valueOf(totalQuantity - 1));
		}
	}

	@Nested
	@DisplayName("uk_limited_purchase")
	class UniqueConstraint {

		@Test
		@DisplayName("같은 회원이 같은 한정반에 중복 저장을 시도하면 제약 위반으로 거절된다")
		void rejectsDuplicateInsertWithoutRedis() {
			// given
			prepareOpenDrop(10);
			limitedDropRedisService.clear(dropId);
			LimitedDrop drop = limitedDropRepository.findById(dropId).orElseThrow();
			Member member = memberRepository.save(MemberFixture.create("uk-" + UUID.randomUUID() + "@groove.com"));

			// when
			limitedPurchaseRepository.saveAndFlush(LimitedPurchaseFixture.create(drop, member));

			// then
			assertThatThrownBy(
					() -> limitedPurchaseRepository.saveAndFlush(LimitedPurchaseFixture.create(drop, member)))
					.isInstanceOf(DataIntegrityViolationException.class);

			assertThat(redisTemplate.hasKey(stockKey())).isFalse();
			assertThat(limitedPurchaseRepository.countByDropId(dropId)).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("expireOrders()")
	class ExpireOrders {

		@Test
		@DisplayName("PENDING 주문이 만료되면 선점이 되돌아가 같은 회원이 재구매할 수 있다")
		void allowsRepurchaseAfterExpiredOrderIsReverted() {
			// given
			int totalQuantity = 5;
			prepareOpenDrop(totalQuantity);
			Buyer buyer = createBuyers(1).get(0);
			LimitedPurchaseResponse purchaseResponse = limitedPurchaseService.purchase(dropId, buyer.memberId(),
					buyer.addressId());

			Order order = orderRepository.findById(purchaseResponse.orderId()).orElseThrow();
			OrderFixture.withExpiresAt(order, LocalDateTime.now(clock).minusMinutes(1));
			orderRepository.saveAndFlush(order);

			// when
			orderExpirationScheduler.expireOrders();

			// then
			Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
			assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(limitedPurchaseRepository.existsByDropIdAndMemberId(dropId, buyer.memberId())).isFalse();

			LimitedDrop reloadedDrop = limitedDropRepository.findById(dropId).orElseThrow();
			assertThat(reloadedDrop.getSoldCount()).isZero();
			assertThat(reloadedDrop.getStatus()).isEqualTo(LimitedDropStatus.OPEN);

			Stock reloadedStock = stockRepository.findByProductId(productId).orElseThrow();
			assertThat(reloadedStock.getQuantity()).isEqualTo(totalQuantity);
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo(String.valueOf(totalQuantity));
			assertThat(redisTemplate.opsForSet().isMember(buyersKey(), String.valueOf(buyer.memberId()))).isFalse();

			LimitedPurchaseResponse repurchase = limitedPurchaseService.purchase(dropId, buyer.memberId(),
					buyer.addressId());

			assertThat(repurchase.orderId()).isNotNull();
			assertThat(limitedPurchaseRepository.countByDropId(dropId)).isEqualTo(1);
		}
	}
}
