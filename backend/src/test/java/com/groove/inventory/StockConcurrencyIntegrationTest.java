package com.groove.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.repository.StockRepository;
import com.groove.inventory.service.StockService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

class StockConcurrencyIntegrationTest extends IntegrationTestSupport {

	private static final int INITIAL_QUANTITY = 10;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private StockService stockService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		executorService = Executors.newFixedThreadPool(2);
	}

	@AfterEach
	void tearDown() {
		executorService.shutdownNow();
	}

	@Nested
	@DisplayName("동시에 재고를 차감하면")
	class ConcurrentDecrease {

		@Test
		@DisplayName("직접 decrease() 를 호출하면 한 스레드만 성공하고 다른 스레드는 낙관적 락 충돌 예외를 받는다")
		void onlyOneThreadSucceedsWhenDecreasingDirectly() throws InterruptedException {
			// given
			Long stockId = createStock(INITIAL_QUANTITY).stockId();
			CyclicBarrier barrier = new CyclicBarrier(2);
			TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
			List<AtomicReference<Throwable>> results = List.of(new AtomicReference<>(), new AtomicReference<>());

			// when
			for (AtomicReference<Throwable> result : results) {
				executorService.submit(() -> runInTransaction(transactionTemplate, barrier, stockId, result));
			}
			executorService.shutdown();
			executorService.awaitTermination(10, TimeUnit.SECONDS);

			// then
			long conflictCount = results.stream()
					.map(AtomicReference::get)
					.filter(throwable -> throwable instanceof ObjectOptimisticLockingFailureException)
					.count();
			assertThat(conflictCount).isEqualTo(1);
			Stock reloaded = stockRepository.findById(stockId).orElseThrow();
			assertThat(reloaded.getQuantity()).isEqualTo(INITIAL_QUANTITY - 1);
		}

		@Test
		@DisplayName("StockService.adjust() 를 동시 호출하면 예외는 모두 낙관적 락 충돌이고 최종 수량은 정합성을 지킨다")
		void keepsConsistentQuantityWhenAdjustingConcurrently() throws InterruptedException {
			// given
			Long productId = createStock(INITIAL_QUANTITY).productId();
			CountDownLatch latch = new CountDownLatch(2);
			List<AtomicReference<Throwable>> results = List.of(new AtomicReference<>(), new AtomicReference<>());

			// when
			for (AtomicReference<Throwable> result : results) {
				executorService.submit(() -> {
					try {
						latch.countDown();
						latch.await();
						stockService.adjust(productId, new StockAdjustRequest(StockChangeType.OUT, 1, "동시성 테스트"));
					} catch (Throwable throwable) {
						result.set(throwable);
					}
				});
			}
			executorService.shutdown();
			executorService.awaitTermination(10, TimeUnit.SECONDS);

			// then
			results.stream()
					.map(AtomicReference::get)
					.filter(throwable -> throwable != null)
					.forEach(throwable -> assertThat(throwable)
							.isInstanceOf(ObjectOptimisticLockingFailureException.class));
			Stock reloaded = stockRepository.findByProductId(productId).orElseThrow();
			assertThat(reloaded.getQuantity()).isBetween(INITIAL_QUANTITY - 2, INITIAL_QUANTITY - 1);
		}
	}

	private void runInTransaction(TransactionTemplate transactionTemplate, CyclicBarrier barrier, Long stockId,
			AtomicReference<Throwable> result) {
		try {
			transactionTemplate.execute(status -> {
				Stock stock = stockRepository.findById(stockId).orElseThrow();
				await(barrier);
				stock.decrease(1);
				return null;
			});
		} catch (Throwable throwable) {
			result.set(throwable);
		}
	}

	private void await(CyclicBarrier barrier) {
		try {
			barrier.await(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private CreatedStock createStock(int quantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist));
		Stock stock = stockRepository.saveAndFlush(StockFixture.create(product, quantity));
		return new CreatedStock(stock.getId(), product.getId());
	}

	private record CreatedStock(Long stockId, Long productId) {
	}
}
