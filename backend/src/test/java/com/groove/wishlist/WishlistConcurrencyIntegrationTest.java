package com.groove.wishlist;

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

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.WishlistFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;
import com.groove.wishlist.repository.WishlistRepository;
import com.groove.wishlist.service.WishlistService;

class WishlistConcurrencyIntegrationTest extends IntegrationTestSupport {

	private static final int REQUEST_COUNT = 10;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private WishlistRepository wishlistRepository;

	@Autowired
	private WishlistService wishlistService;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		executorService = Executors.newFixedThreadPool(REQUEST_COUNT);
	}

	@AfterEach
	void tearDown() {
		executorService.shutdownNow();
	}

	@Nested
	@DisplayName("같은 회원이 같은 상품을 동시에 위시리스트에 담으면")
	class ConcurrentAdd {

		@Test
		@DisplayName("1건만 성공하고 나머지는 WISHLIST_ALREADY_EXISTS 로 실패한다")
		void onlyOneWishlistItemIsCreated() throws InterruptedException {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			Member member = memberRepository.save(
					MemberFixture.create("wisher-" + UUID.randomUUID() + "@groove.com"));

			Long productId = product.getId();
			Long memberId = member.getId();

			CountDownLatch readyLatch = new CountDownLatch(REQUEST_COUNT);
			CountDownLatch startLatch = new CountDownLatch(1);
			List<AtomicReference<Throwable>> results = new ArrayList<>();
			AtomicInteger successCount = new AtomicInteger();

			// when
			for (int i = 0; i < REQUEST_COUNT; i++) {
				AtomicReference<Throwable> result = new AtomicReference<>();
				results.add(result);
				executorService.submit(() -> {
					try {
						readyLatch.countDown();
						startLatch.await();
						wishlistService.add(memberId, WishlistFixture.addRequest(productId));
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
			assertThat(failures).hasSize(REQUEST_COUNT - 1);
			// 선검사에서 걸리든, 유니크 제약 위반이든, 같은 키 INSERT 데드락이든 결과 코드는 같아야 한다.
			failures.forEach(throwable -> assertThat(throwable)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.WISHLIST_ALREADY_EXISTS));

			assertThat(wishlistRepository.findByMemberIdAndProductId(memberId, productId)).isPresent();
		}
	}
}
