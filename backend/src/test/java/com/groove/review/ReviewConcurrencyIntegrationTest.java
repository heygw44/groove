package com.groove.review;

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
import org.springframework.data.domain.PageRequest;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ReviewFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.review.repository.ReviewRepository;
import com.groove.review.service.ReviewService;
import com.groove.support.IntegrationTestSupport;

class ReviewConcurrencyIntegrationTest extends IntegrationTestSupport {

	private static final int REQUEST_COUNT = 10;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private ReviewService reviewService;

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
	@DisplayName("같은 회원이 같은 상품에 동시에 리뷰를 작성하면")
	class ConcurrentCreate {

		@Test
		@DisplayName("1건만 성공하고 나머지는 REVIEW_ALREADY_EXISTS 로 실패한다")
		void onlyOneReviewIsCreated() throws InterruptedException {
			// given: 구매 검증을 통과시키려면 DELIVERED 주문이 필요해 REST 흐름 없이 바로 심는다.
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			Member member = memberRepository.save(
					MemberFixture.create("reviewer-" + UUID.randomUUID() + "@groove.com"));
			Order order = OrderFixture.createWithItem(member, product, 1);
			orderRepository.save(OrderFixture.markDelivered(order));

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
						reviewService.create(productId, memberId, ReviewFixture.createRequest());
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
			// 선검사에서 걸리든 유니크 제약 위반을 잡아 변환하든 결과 코드는 같아야 한다.
			failures.forEach(throwable -> assertThat(throwable)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS));

			assertThat(reviewRepository.findByProductId(productId, PageRequest.of(0, REQUEST_COUNT))
					.getTotalElements()).isEqualTo(1);
			Product reloadedProduct = productRepository.findById(productId).orElseThrow();
			assertThat(reloadedProduct.getAverageRating()).isEqualByComparingTo("5.0");
			assertThat(reloadedProduct.getReviewCount()).isEqualTo(1);
		}
	}
}
