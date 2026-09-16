package com.groove.limited;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.limited.scheduler.LimitedDropReconcileScheduler;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedDropSyncService;
import com.groove.limited.service.LimitedPurchaseService;
import com.groove.limited.service.LimitedSyncResult;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/** Redis 선점 누수 대사(LimitedDropSyncService.sync/LimitedDropReconcileScheduler)를 검증한다. */
class LimitedDropReconcileIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

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
	private LimitedDropSyncService limitedDropSyncService;

	@Autowired
	private LimitedDropReconcileScheduler limitedDropReconcileScheduler;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private LimitedPurchaseService limitedPurchaseService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private Clock clock;

	private Long dropId;

	@AfterEach
	void tearDown() {
		limitedDropRedisService.clear(dropId);
	}

	private Long prepareOpenDrop(int totalQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, totalQuantity));

		LimitedDrop drop = LimitedDropFixture.scheduled(product, totalQuantity, Math.min(2, totalQuantity));
		drop.open();
		LocalDateTime now = LocalDateTime.now(clock);
		LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
		LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
		limitedDropRepository.saveAndFlush(drop);

		dropId = drop.getId();
		// create-drop 로 PK 가 재사용될 수 있어, 다른 테스트가 남긴 낡은 키를 먼저 지우고 초기화한다.
		limitedDropRedisService.clear(dropId);
		limitedDropRedisService.initStock(dropId, totalQuantity);
		return dropId;
	}

	private Member createMember() {
		return memberRepository.save(MemberFixture.create("reconcile-" + UUID.randomUUID() + "@groove.com"));
	}

	private String stockKey() {
		return LimitedDropRedisService.stockKey(dropId);
	}

	private String buyersKey() {
		return LimitedDropRedisService.buyersKey(dropId);
	}

	private String pendingKey() {
		return LimitedDropRedisService.pendingKey(dropId);
	}

	@Nested
	@DisplayName("sync()")
	class Sync {

		@Test
		@DisplayName("write() 없이 선점만 한 뒤 pending 이 grace 를 넘기면 재고를 복구하고 재구매가 가능해진다")
		void restoresLeakedReservationAfterGraceElapses() {
			// given
			prepareOpenDrop(5);
			Member member = createMember();
			Address address = addressRepository.save(AddressFixture.create(member));
			limitedDropRedisService.reserve(dropId, member.getId());
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("4");
			// pending 시각을 grace(30s) 이전으로 밀어 write() 가 끝내 오지 않은 선점처럼 만든다.
			redisTemplate.opsForZSet().add(pendingKey(), member.getId().toString(), clock.millis() - 60_000);

			// when
			Optional<LimitedSyncResult> result = limitedDropSyncService.sync(dropId);

			// then
			assertThat(result).isPresent();
			assertThat(result.get().leaked()).isEqualTo(1);
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("5");
			assertThat(redisTemplate.opsForSet().isMember(buyersKey(), member.getId().toString())).isFalse();
			assertThat(redisTemplate.opsForZSet().score(pendingKey(), member.getId().toString())).isNull();

			LimitedPurchaseResponse response = limitedPurchaseService.purchase(dropId, member.getId(),
					address.getId());
			assertThat(response.orderId()).isNotNull();
		}

		@Test
		@DisplayName("정상 구매 뒤 pending 이 다시 남아 있어도(confirm 누락 흉내) 재고는 그대로고 pending 만 비운다")
		void clearsStalePendingWithoutTouchingStockAfterSuccessfulPurchase() {
			// given
			prepareOpenDrop(5);
			Member member = createMember();
			Address address = addressRepository.save(AddressFixture.create(member));
			limitedPurchaseService.purchase(dropId, member.getId(), address.getId());
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("4");
			// confirm() 이 안 된 것처럼 pending 에 다시 넣는다.
			redisTemplate.opsForZSet().add(pendingKey(), member.getId().toString(), clock.millis());

			// when
			Optional<LimitedSyncResult> result = limitedDropSyncService.sync(dropId);

			// then
			assertThat(result).isPresent();
			assertThat(result.get().cleared()).isEqualTo(1);
			assertThat(result.get().leaked()).isZero();
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("4");
			assertThat(redisTemplate.opsForSet().isMember(buyersKey(), member.getId().toString())).isTrue();
			assertThat(redisTemplate.opsForZSet().score(pendingKey(), member.getId().toString())).isNull();
		}

		@Test
		@DisplayName("Redis 재고가 변조되면 DB 잔여 수량과 일치하도록 되돌린다")
		void realignsTamperedStockWithDbRemaining() {
			// given
			prepareOpenDrop(10);
			redisTemplate.opsForValue().increment(stockKey(), 3);
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("13");

			// when
			limitedDropSyncService.sync(dropId);

			// then
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("10");

			// and: 반대로 모자라게 변조해도 마찬가지다
			redisTemplate.opsForValue().decrement(stockKey(), 3);
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("7");

			limitedDropSyncService.sync(dropId);

			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("10");
		}

		@Test
		@DisplayName("신선한 pending(진행 중 선점)은 건드리지 않고 재고에서만 뺀다")
		void leavesFreshPendingReservationUntouched() {
			// given
			prepareOpenDrop(5);
			Member member = createMember();
			limitedDropRedisService.reserve(dropId, member.getId());

			// when: write() 가 아직 커밋되지 않은 것처럼 DB 는 5 를 그대로 갖고 있다
			Optional<LimitedSyncResult> result = limitedDropSyncService.sync(dropId);

			// then
			assertThat(result).isPresent();
			assertThat(result.get().changed()).isFalse();
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("4");
			assertThat(redisTemplate.opsForSet().isMember(buyersKey(), member.getId().toString())).isTrue();
			assertThat(redisTemplate.opsForZSet().score(pendingKey(), member.getId().toString())).isNotNull();
		}
	}

	@Nested
	@DisplayName("대사 스레드와 동시 구매")
	class ConcurrentReconcileDuringPurchase {

		@Test
		@DisplayName("대사 스레드가 반복 sync 하는 동안 동시 구매해도 재고만큼만 성공하고 마지막 sync 는 변화가 없다")
		void sellsExactlyStockQuantityWhileReconciling() throws InterruptedException {
			// given
			int totalQuantity = 50;
			int memberCount = 200;
			prepareOpenDrop(totalQuantity);
			List<Buyer> buyers = createBuyers(memberCount);

			AtomicBoolean keepReconciling = new AtomicBoolean(true);
			ExecutorService reconcileExecutor = Executors.newSingleThreadExecutor();
			reconcileExecutor.submit(() -> {
				while (keepReconciling.get()) {
					limitedDropSyncService.sync(dropId);
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			});

			// when
			ConcurrencyResult result = runConcurrently(memberCount, i -> {
				Buyer buyer = buyers.get(i);
				return () -> limitedPurchaseService.purchase(dropId, buyer.memberId(), buyer.addressId());
			});

			keepReconciling.set(false);
			reconcileExecutor.shutdown();
			assertThat(reconcileExecutor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

			// then
			assertThat(result.finished()).isTrue();
			assertThat(result.successCount()).isEqualTo(totalQuantity);
			assertThat(limitedPurchaseRepository.countByDropId(dropId)).isEqualTo((long) totalQuantity);
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("0");

			Optional<LimitedSyncResult> finalResult = limitedDropSyncService.sync(dropId);
			assertThat(finalResult).isPresent();
			assertThat(finalResult.get().changed()).isFalse();
		}
	}

	@Nested
	@DisplayName("LimitedDropReconcileScheduler.reconcile()")
	class Reconcile {

		@Test
		@DisplayName("OPEN 드롭의 새어 나간 선점을 대사 스케줄러 직접 호출로 복구한다")
		void restoresLeakedReservationViaScheduler() {
			// given
			prepareOpenDrop(3);
			Member member = createMember();
			limitedDropRedisService.reserve(dropId, member.getId());
			redisTemplate.opsForZSet().add(pendingKey(), member.getId().toString(), clock.millis() - 60_000);

			// when
			limitedDropReconcileScheduler.reconcile();

			// then
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("3");
			assertThat(redisTemplate.opsForSet().isMember(buyersKey(), member.getId().toString())).isFalse();
		}
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

	private record Buyer(Long memberId, Long addressId) {
	}

	private record ConcurrencyResult(boolean finished, int successCount, List<Throwable> failures) {
	}
}
