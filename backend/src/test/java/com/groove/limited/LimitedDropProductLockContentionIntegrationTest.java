package com.groove.limited;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.product.service.AdminProductService;
import com.groove.support.IntegrationTestSupport;

/**
 * findByIdForUpdate() 가 드롭 행 락을 쥐고 있는 동안, 같은 상품을 건드리는 다른 트랜잭션이
 * 막히지 않고 끝나는지 확인한다. product 를 fetch join 하던 시절에는 이 트랜잭션이
 * 드롭 락 보유 시간만큼 대기했다(실측으로 확인).
 */
class LimitedDropProductLockContentionIntegrationTest extends IntegrationTestSupport {

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
	private MemberRepository memberRepository;

	@Autowired
	private AdminProductService adminProductService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Nested
	@DisplayName("findByIdForUpdate()")
	class FindByIdForUpdate {

		@Test
		@DisplayName("드롭 행 락을 쥔 동안에도 같은 상품을 숨기는 관리자 요청이 곧바로 끝난다")
		void doesNotBlockConcurrentProductUpdate() throws Exception {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product createdProduct = ProductFixture.create(artist);
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);
			stockRepository.saveAndFlush(StockFixture.create(product, 10));
			LimitedDrop drop = limitedDropRepository.saveAndFlush(LimitedDropFixture.scheduled(product));
			Long dropId = drop.getId();
			Long productId = product.getId();
			Long adminId = memberRepository.save(MemberFixture.createAdmin()).getId();

			CountDownLatch lockAcquired = new CountDownLatch(1);
			CountDownLatch releaseLock = new CountDownLatch(1);

			// when: 한 스레드가 드롭 락을 쥐고 넉넉히(10초) 대기한다
			TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
			Thread holder = new Thread(() -> txTemplate.executeWithoutResult(status -> {
				limitedDropRepository.findByIdForUpdate(dropId).orElseThrow();
				lockAcquired.countDown();
				try {
					releaseLock.await(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));
			holder.start();

			boolean acquired = lockAcquired.await(10, TimeUnit.SECONDS);
			assertThat(acquired).isTrue();

			try {
				// 같은 상품을 숨기는 별도 트랜잭션(실제 관리자 상품 수정 경로)
				CompletableFuture<Void> concurrentUpdate = CompletableFuture.runAsync(
						() -> adminProductService.hide(adminId, productId));

				// then: 드롭 락 보유 시간(10초)보다 훨씬 짧은 시간 안에 끝나야 한다 = product 행은 잠기지 않았다
				assertThatCode(() -> concurrentUpdate.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
			} finally {
				releaseLock.countDown();
				holder.join(Duration.ofSeconds(10).toMillis());
			}

			Product hidden = productRepository.findById(productId).orElseThrow();
			assertThat(hidden.isHidden()).isTrue();
		}
	}
}
