package com.groove.coupon;

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

import com.groove.coupon.dto.CouponIssueRequest;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.repository.CouponRepository;
import com.groove.coupon.repository.MemberCouponRepository;
import com.groove.coupon.service.MemberCouponService;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.support.IntegrationTestSupport;

class CouponIssueConcurrencyIntegrationTest extends IntegrationTestSupport {

	private static final int TOTAL_QUANTITY = 10;
	private static final int MEMBER_COUNT = 20;

	@Autowired
	private CouponRepository couponRepository;

	@Autowired
	private MemberCouponRepository memberCouponRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private MemberCouponService memberCouponService;

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
	@DisplayName("동시에 같은 쿠폰을 발급 요청하면")
	class ConcurrentIssue {

		@Test
		@DisplayName("발급 수량만큼만 성공하고 나머지는 COUPON_SOLD_OUT 으로 실패한다")
		void onlyTotalQuantityIssuesSucceed() throws InterruptedException {
			// given
			String code = "CONC" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
			Coupon coupon = couponRepository.save(CouponFixture.withTotalQuantity(code, TOTAL_QUANTITY));

			List<Long> memberIds = new ArrayList<>();
			for (int i = 0; i < MEMBER_COUNT; i++) {
				Member member = memberRepository.save(
						MemberFixture.create("coupon-buyer-" + UUID.randomUUID() + "@groove.com"));
				memberIds.add(member.getId());
			}

			CountDownLatch readyLatch = new CountDownLatch(MEMBER_COUNT);
			CountDownLatch startLatch = new CountDownLatch(1);
			List<AtomicReference<Throwable>> results = new ArrayList<>();
			AtomicInteger successCount = new AtomicInteger();

			// when
			for (Long memberId : memberIds) {
				AtomicReference<Throwable> result = new AtomicReference<>();
				results.add(result);
				executorService.submit(() -> {
					try {
						readyLatch.countDown();
						startLatch.await();
						memberCouponService.issue(memberId, new CouponIssueRequest(code));
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
			long failureCount = results.stream()
					.map(AtomicReference::get)
					.filter(throwable -> throwable != null)
					.count();
			assertThat(failureCount).isEqualTo(MEMBER_COUNT - TOTAL_QUANTITY);
			results.stream()
					.map(AtomicReference::get)
					.filter(throwable -> throwable != null)
					.forEach(throwable -> assertThat(throwable)
							.isInstanceOf(BusinessException.class)
							.extracting("errorCode")
							.isEqualTo(ErrorCode.COUPON_SOLD_OUT));

			Coupon reloadedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
			assertThat(reloadedCoupon.getIssuedCount()).isEqualTo(TOTAL_QUANTITY);
			long issuedCount = memberIds.stream()
					.filter(memberId -> memberCouponRepository.existsByMemberIdAndCouponId(memberId,
							coupon.getId()))
					.count();
			assertThat(issuedCount).isEqualTo(TOTAL_QUANTITY);
		}
	}
}
