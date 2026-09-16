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
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.limited.scheduler.LimitedDropScheduler;
import com.groove.limited.service.AdminLimitedDropService;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedPurchaseService;
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

/** Redis stock/buyers 키 유실 시 구매 경로·스케줄러·관리자 재오픈으로 재적재되는지 검증한다. */
class LimitedDropRebuildIntegrationTest extends IntegrationTestSupport {

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
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private LimitedPurchaseService limitedPurchaseService;

	@Autowired
	private LimitedDropScheduler limitedDropScheduler;

	@Autowired
	private AdminLimitedDropService adminLimitedDropService;

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

	private Buyer createBuyer() {
		Member member = memberRepository.save(MemberFixture.create("rebuild-" + UUID.randomUUID() + "@groove.com"));
		Address address = addressRepository.save(AddressFixture.create(member));
		return new Buyer(member.getId(), address.getId());
	}

	private List<Buyer> createBuyers(int count) {
		List<Buyer> buyers = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			buyers.add(createBuyer());
		}
		return buyers;
	}

	private void deleteRedisKeys() {
		redisTemplate.delete(List.of(LimitedDropRedisService.stockKey(dropId),
				LimitedDropRedisService.buyersKey(dropId)));
	}

	private String stockKey() {
		return LimitedDropRedisService.stockKey(dropId);
	}

	private String buyersKey() {
		return LimitedDropRedisService.buyersKey(dropId);
	}

	private record Buyer(Long memberId, Long addressId) {
	}

	private record ConcurrencyResult(boolean finished, int successCount, List<Throwable> failures) {
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

	@Nested
	@DisplayName("purchase() - 키 유실 재적재")
	class PurchaseAfterKeyLoss {

		@Test
		@DisplayName("stock/buyers 키가 사라지면 다음 구매에서 재적재하고 이미 산 회원은 다시 구매할 수 없다")
		void rebuildsKeysOnNextPurchaseAndRejectsExistingBuyer() {
			// given
			prepareOpenDrop(5);
			Buyer firstBuyer = createBuyer();
			limitedPurchaseService.purchase(dropId, firstBuyer.memberId(), firstBuyer.addressId());
			deleteRedisKeys();
			Buyer secondBuyer = createBuyer();

			// when
			LimitedPurchaseResponse response = limitedPurchaseService.purchase(dropId, secondBuyer.memberId(),
					secondBuyer.addressId());

			// then
			assertThat(response.orderId()).isNotNull();
			assertThatThrownBy(
					() -> limitedPurchaseService.purchase(dropId, firstBuyer.memberId(), firstBuyer.addressId()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_ALREADY_PURCHASED);

			LimitedDrop reloaded = limitedDropRepository.findById(dropId).orElseThrow();
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo(
					String.valueOf(reloaded.remainingQuantity()));
			assertThat(redisTemplate.opsForSet().isMember(buyersKey(), firstBuyer.memberId().toString())).isTrue();
			assertThat(redisTemplate.opsForSet().isMember(buyersKey(), secondBuyer.memberId().toString())).isTrue();
		}

		@Test
		@DisplayName("키가 사라진 채 여러 회원이 동시에 구매해도 재고만큼만 성공하고 초과 판매는 없다")
		void sellsExactlyStockQuantityEvenAfterKeyLoss() throws InterruptedException {
			// given
			int totalQuantity = 50;
			int memberCount = 200;
			prepareOpenDrop(totalQuantity);
			deleteRedisKeys();
			List<Buyer> buyers = createBuyers(memberCount);

			// when
			ConcurrencyResult result = runConcurrently(memberCount, i -> {
				Buyer buyer = buyers.get(i);
				return () -> limitedPurchaseService.purchase(dropId, buyer.memberId(), buyer.addressId());
			});

			// then
			assertThat(result.finished()).isTrue();
			assertThat(result.successCount()).isEqualTo(totalQuantity);
			assertThat(limitedPurchaseRepository.countByDropId(dropId)).isEqualTo((long) totalQuantity);
			result.failures().forEach(throwable -> assertThat(throwable)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isIn(ErrorCode.LIMITED_SOLD_OUT, ErrorCode.LIMITED_ALREADY_PURCHASED, ErrorCode.LIMITED_NOT_OPEN));
		}
	}

	@Nested
	@DisplayName("LimitedDropScheduler.run()")
	class SchedulerRestore {

		@Test
		@DisplayName("OPEN 드롭의 유실된 키를 스케줄러 실행만으로 복구한다")
		void restoresMissingKeysDuringScheduledRun() {
			// given
			prepareOpenDrop(5);
			Buyer buyer = createBuyer();
			limitedPurchaseService.purchase(dropId, buyer.memberId(), buyer.addressId());
			deleteRedisKeys();

			// when
			limitedDropScheduler.run();

			// then
			LimitedDrop reloaded = limitedDropRepository.findById(dropId).orElseThrow();
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo(
					String.valueOf(reloaded.remainingQuantity()));
			assertThat(redisTemplate.opsForSet().isMember(buyersKey(), buyer.memberId().toString())).isTrue();
		}
	}

	@Nested
	@DisplayName("AdminLimitedDropService.open() 재호출")
	class AdminReopen {

		@Test
		@DisplayName("이미 OPEN 인 드롭을 다시 열면 Redis 구매자 집합이 DB 와 일치하도록 복구된다")
		void resyncsBuyersToMatchDbOnReopen() {
			// given
			prepareOpenDrop(5);
			Buyer buyer = createBuyer();
			limitedPurchaseService.purchase(dropId, buyer.memberId(), buyer.addressId());
			deleteRedisKeys();

			// when
			adminLimitedDropService.open(999L, dropId);

			// then
			LimitedDrop reloaded = limitedDropRepository.findById(dropId).orElseThrow();
			assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo(
					String.valueOf(reloaded.remainingQuantity()));
			assertThat(redisTemplate.opsForSet().members(buyersKey())).containsExactly(buyer.memberId().toString());
		}
	}
}
