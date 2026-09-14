package com.groove.limited;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
import com.groove.limited.config.LimitedCircuitProperties;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedPurchaseService;
import com.groove.limited.service.LimitedRedisCircuitBreaker;
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

/** Redis 장애 서킷·DB 폴백(LimitedRedisCircuitBreaker/LimitedFallbackGate)을 검증한다. */
class LimitedRedisFallbackIntegrationTest extends IntegrationTestSupport {

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
	private LimitedPurchaseService limitedPurchaseService;

	@Autowired
	private LimitedRedisCircuitBreaker limitedRedisCircuitBreaker;

	@Autowired
	private LimitedCircuitProperties limitedCircuitProperties;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private Clock clock;

	private Long dropId;

	@AfterEach
	void tearDown() throws InterruptedException {
		try {
			// 테스트 본문에서 이미 unpause 했을 수 있다(복구 시나리오). 이미 풀린 컨테이너를 또 풀려 하면
			// Docker 가 500 을 돌려주므로 무시한다.
			unpauseRedis();
		} catch (RuntimeException ignored) {
			// no-op
		}
		// 서킷이 싱글턴이라 상태가 남으면 뒤에 도는 다른 한정반 테스트가 전부 폴백으로 빠진다.
		// open-duration(1s, test 프로필) 이 지날 때까지 기다린 뒤 프로브를 잡아 강제로 CLOSED 로 되돌린다.
		Thread.sleep(1_200);
		for (int i = 0; i < 10 && limitedRedisCircuitBreaker.state() != LimitedRedisCircuitBreaker.State.CLOSED; i++) {
			if (limitedRedisCircuitBreaker.allowRedis()) {
				limitedRedisCircuitBreaker.onSuccess();
			}
		}
		if (dropId != null) {
			limitedRedisCircuitBreaker.clearFallbackDrops(Set.of(dropId));
			limitedDropRedisService.clear(dropId);
		}
	}

	private Long prepareOpenDrop(int totalQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, totalQuantity));

		LimitedDrop drop = LimitedDropFixture.scheduled(product, totalQuantity, totalQuantity);
		drop.open();
		LocalDateTime now = LocalDateTime.now(clock);
		LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
		LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
		limitedDropRepository.saveAndFlush(drop);

		dropId = drop.getId();
		limitedDropRedisService.clear(dropId);
		limitedDropRedisService.initStock(dropId, totalQuantity);
		return dropId;
	}

	private Buyer createBuyer() {
		Member member = memberRepository.save(MemberFixture.create("fallback-" + UUID.randomUUID() + "@groove.com"));
		Address address = addressRepository.save(AddressFixture.create(member));
		return new Buyer(member.getId(), address.getId());
	}

	@Test
	@DisplayName("Redis 가 멈추면 연속 실패 threshold 만큼 DB 경로로 성공 처리하고 서킷이 OPEN 된다")
	void fallsBackToDbAndOpensCircuitWhenRedisIsDown() {
		// given
		prepareOpenDrop(50);
		pauseRedis();
		int threshold = limitedCircuitProperties.failureThreshold();

		// when: 순차 구매 threshold 건 모두 Redis 없이 DB 경로로 성공한다
		for (int i = 0; i < threshold; i++) {
			Buyer buyer = createBuyer();
			assertThat(limitedPurchaseService.purchase(dropId, buyer.memberId(), buyer.addressId()).orderId())
					.isNotNull();
		}

		// then: openDuration(1s, test 프로필) 이 짧아 마지막 실패의 DB 쓰기만으로도 이미 HALF_OPEN 으로 넘어갈 수
		// 있으니 "더는 CLOSED 가 아니다" 로 검증한다.
		assertThat(limitedRedisCircuitBreaker.state()).isNotEqualTo(LimitedRedisCircuitBreaker.State.CLOSED);
		assertThat(limitedPurchaseRepository.countByDropId(dropId)).isEqualTo((long) threshold);
	}

	@Test
	@DisplayName("서킷 OPEN 중 동시 요청이 몰리면 폴백 게이트 permit 만큼만 통과하고 나머지는 LIMITED_BUSY 다")
	void limitsConcurrentFallbackRequestsWhilePermitsAreExhausted() throws InterruptedException {
		// given
		int totalQuantity = 50;
		prepareOpenDrop(totalQuantity);
		pauseRedis();
		int threshold = limitedCircuitProperties.failureThreshold();
		for (int i = 0; i < threshold; i++) {
			Buyer buyer = createBuyer();
			limitedPurchaseService.purchase(dropId, buyer.memberId(), buyer.addressId());
		}
		assertThat(limitedRedisCircuitBreaker.state()).isNotEqualTo(LimitedRedisCircuitBreaker.State.CLOSED);

		int concurrentCount = 40;
		List<Buyer> buyers = new ArrayList<>();
		for (int i = 0; i < concurrentCount; i++) {
			buyers.add(createBuyer());
		}

		// when
		ConcurrencyResult result = runConcurrently(concurrentCount,
				i -> () -> limitedPurchaseService.purchase(dropId, buyers.get(i).memberId(),
						buyers.get(i).addressId()));

		// then
		assertThat(result.finished()).isTrue();
		long busyCount = result.failureCodes().stream().filter(code -> code == ErrorCode.LIMITED_BUSY).count();
		assertThat(result.successCount() + busyCount).isEqualTo((long) concurrentCount);
		assertThat(result.failureCodes()).allMatch(code -> code == ErrorCode.LIMITED_BUSY);
		assertThat((long) result.successCount()).isLessThanOrEqualTo(totalQuantity - threshold);
	}

	@Test
	@DisplayName("Redis 가 돌아오고 openDuration 이 지나면 구매 1건으로 서킷이 CLOSED 되고 Redis 가 DB 와 다시 맞는다")
	void closesCircuitAndResyncsRedisAfterRedisRecovers() throws InterruptedException {
		// given
		prepareOpenDrop(50);
		pauseRedis();
		int threshold = limitedCircuitProperties.failureThreshold();
		for (int i = 0; i < threshold; i++) {
			Buyer buyer = createBuyer();
			limitedPurchaseService.purchase(dropId, buyer.memberId(), buyer.addressId());
		}
		assertThat(limitedRedisCircuitBreaker.state()).isNotEqualTo(LimitedRedisCircuitBreaker.State.CLOSED);

		// when
		unpauseRedis();
		Thread.sleep(1_200);
		Buyer probeBuyer = createBuyer();
		assertThat(limitedPurchaseService.purchase(dropId, probeBuyer.memberId(), probeBuyer.addressId()).orderId())
				.isNotNull();

		// then
		assertThat(limitedRedisCircuitBreaker.state()).isEqualTo(LimitedRedisCircuitBreaker.State.CLOSED);
		int dbRemaining = totalRemainingQuantity();
		assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId)))
				.isEqualTo(String.valueOf(dbRemaining));
		Set<String> dbBuyerIds = limitedPurchaseRepository.findMemberIdsByDropId(dropId).stream()
				.map(String::valueOf)
				.collect(Collectors.toSet());
		assertThat(redisTemplate.opsForSet().members(LimitedDropRedisService.buyersKey(dropId)))
				.isEqualTo(dbBuyerIds);
	}

	private int totalRemainingQuantity() {
		return limitedDropRepository.findById(dropId).orElseThrow().remainingQuantity();
	}

	private ConcurrencyResult runConcurrently(int threads, IntFunction<Runnable> taskFactory)
			throws InterruptedException {
		ExecutorService executorService = Executors.newFixedThreadPool(threads);
		CountDownLatch readyLatch = new CountDownLatch(threads);
		CountDownLatch startLatch = new CountDownLatch(1);
		AtomicInteger successCount = new AtomicInteger();
		List<ErrorCode> failureCodes = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			Runnable task = taskFactory.apply(i);
			executorService.submit(() -> {
				try {
					readyLatch.countDown();
					startLatch.await();
					task.run();
					successCount.incrementAndGet();
				} catch (BusinessException e) {
					synchronized (failureCodes) {
						failureCodes.add(e.getErrorCode());
					}
				} catch (Throwable throwable) {
					synchronized (failureCodes) {
						failureCodes.add(null);
					}
				}
			});
		}
		readyLatch.await();
		startLatch.countDown();
		executorService.shutdown();
		boolean finished = executorService.awaitTermination(60, TimeUnit.SECONDS);

		return new ConcurrencyResult(finished, successCount.get(), failureCodes);
	}

	private record Buyer(Long memberId, Long addressId) {
	}

	private record ConcurrencyResult(boolean finished, int successCount, List<ErrorCode> failureCodes) {
	}
}
