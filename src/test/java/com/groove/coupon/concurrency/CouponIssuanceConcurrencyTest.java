package com.groove.coupon.concurrency;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.coupon.application.CouponIssueService;
import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.exception.CouponAlreadyIssuedException;
import com.groove.coupon.exception.CouponSoldOutException;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 쿠폰 발급 동시성 테스트 (#90, decisions/coupon-concurrency.md).
 *
 * <p>오버셀 baseline(#46)과 같은 서사의 쿠폰판이다 — 베이스라인(락 없음)은 초과발급을 재현({@code @Disabled}
 * 로 보존)하고, 프로덕션 경로(원자적 조건부 UPDATE)는 초과발급 0 을 증명한다. ([OversellingBaselineTest]
 * 포맷 재사용)
 *
 * <p>회원당 1장 UNIQUE 때문에 초과발급 노출은 <b>서로 다른 member_id</b> 로 글로벌 카운터를 때려야 한다
 * (이슈 #90 비고). 멱등 재시도 부수효과 1회는 엔드포인트 레벨에서 {@code CouponApiIntegrationTest}
 * (Idempotency-Key replay)가 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("선착순 쿠폰 발급 동시성 (#90)")
class CouponIssuanceConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(CouponIssuanceConcurrencyTest.class);

    private static final int LIMIT = 100;
    private static final int CONCURRENT_REQUESTS = 300;
    private static final int THREAD_POOL_SIZE = 32;
    private static final long DONE_GATE_TIMEOUT_SECONDS = 60L;
    private static final Instant VALID_FROM = Instant.now().minus(1, ChronoUnit.DAYS);
    private static final Instant VALID_UNTIL = Instant.now().plus(10, ChronoUnit.DAYS);

    @Autowired
    private CouponIssueService couponIssueService;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private MemberCouponRepository memberCouponRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        cleanupAll();
    }

    @AfterEach
    void tearDown() {
        cleanupAll();
    }

    private void cleanupAll() {
        // FK 의존 순서: member_coupon → coupon, member. refresh_token → member.
        refreshTokenRepository.deleteAllInBatch();
        memberCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("원자적 UPDATE — 한정 100 / 동시 300(서로 다른 회원) → 정확히 100 발급, 초과발급 0")
    void atomicUpdate_noOverIssuance() throws InterruptedException {
        Long couponId = persistLimitedCoupon(LIMIT);
        List<Long> memberIds = createMembers(CONCURRENT_REQUESTS);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        runConcurrently(CONCURRENT_REQUESTS, i -> {
            try {
                couponIssueService.issue(memberIds.get(i), couponId);
                success.incrementAndGet();
            } catch (CouponSoldOutException ex) {
                soldOut.incrementAndGet();
            } catch (RuntimeException ex) {
                other.incrementAndGet();
                log.warn("예상 외 예외", ex);
            }
        });

        int issuedCount = couponRepository.findById(couponId).orElseThrow().getIssuedCount();
        long persisted = memberCouponRepository.count();
        log.info("[#90 원자적] success={}, soldOut={}, other={}, issuedCount={}, persisted={}",
                success.get(), soldOut.get(), other.get(), issuedCount, persisted);

        assertThat(success.get()).as("정확히 한정수량만 발급").isEqualTo(LIMIT);
        assertThat(issuedCount).as("issued_count == 한정수량 (초과발급 0)").isEqualTo(LIMIT);
        assertThat(persisted).as("영속 member_coupon 수 == issued_count").isEqualTo(LIMIT);
        assertThat(soldOut.get()).as("나머지는 소진 거부").isEqualTo(CONCURRENT_REQUESTS - LIMIT);
        assertThat(other.get()).isZero();
    }

    @Test
    @DisplayName("같은 회원 동시·중복 요청 → 정확히 1장 (UNIQUE + 트랜잭션 롤백으로 슬롯 누수 0)")
    void sameMember_concurrentDuplicate_exactlyOne() throws InterruptedException {
        Long couponId = persistLimitedCoupon(LIMIT);
        Long memberId = createMembers(1).get(0);
        int attempts = 24;

        AtomicInteger success = new AtomicInteger();
        AtomicInteger alreadyIssued = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        runConcurrently(attempts, i -> {
            try {
                couponIssueService.issue(memberId, couponId);
                success.incrementAndGet();
            } catch (CouponAlreadyIssuedException ex) {
                alreadyIssued.incrementAndGet();
            } catch (RuntimeException ex) {
                other.incrementAndGet();
                log.warn("예상 외 예외", ex);
            }
        });

        int issuedCount = couponRepository.findById(couponId).orElseThrow().getIssuedCount();
        long persisted = memberCouponRepository.count();
        log.info("[#90 같은회원] success={}, alreadyIssued={}, other={}, issuedCount={}, persisted={}",
                success.get(), alreadyIssued.get(), other.get(), issuedCount, persisted);

        assertThat(success.get()).as("정확히 1장만 발급").isEqualTo(1);
        assertThat(persisted).as("member_coupon 정확히 1건").isEqualTo(1);
        assertThat(issuedCount).as("UNIQUE 충돌 롤백으로 카운터 누수 없음").isEqualTo(1);
        assertThat(other.get()).isZero();
    }

    @Test
    @Disabled("초과발급 baseline 시연용 — 락 없는 read-modify-write 의 결함 재현. 일반 CI 빌드에서는 실행하지 않는다 (#90)")
    @DisplayName("베이스라인(락 없음) — 동시 발급 시 lost-update 로 초과발급 재현")
    void baseline_withoutLock_overIssues() throws InterruptedException {
        Long couponId = persistLimitedCoupon(LIMIT);
        List<Long> memberIds = createMembers(CONCURRENT_REQUESTS);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        runConcurrently(CONCURRENT_REQUESTS, i -> {
            try {
                couponIssueService.issueWithoutLock(memberIds.get(i), couponId);
                success.incrementAndGet();
            } catch (CouponSoldOutException ex) {
                soldOut.incrementAndGet();
            } catch (RuntimeException ex) {
                other.incrementAndGet();
            }
        });

        int issuedCount = couponRepository.findById(couponId).orElseThrow().getIssuedCount();
        long persisted = memberCouponRepository.count();
        log.info("[#90 baseline] success={}, soldOut={}, other={}, issuedCount={}, persisted={} (limit={})",
                success.get(), soldOut.get(), other.get(), issuedCount, persisted, LIMIT);

        // 통과 조건 = "초과발급 증거". lost-update 시 다음 중 하나 이상이 성립한다:
        //   (1) persisted > LIMIT          — 한정수량보다 많이 발급 (오버이슈)
        //   (2) persisted > issuedCount    — 영속 발급 수가 카운터를 초과 (lost-update 직접 증거)
        //   (3) success > LIMIT            — 한정수량 초과 성공
        boolean overIssued = persisted > LIMIT || persisted > issuedCount || success.get() > LIMIT;
        assertThat(overIssued)
                .as("baseline 초과발급 — persisted(%d)>limit(%d) 또는 persisted>issuedCount(%d) 또는 success(%d)>limit",
                        persisted, LIMIT, issuedCount, success.get())
                .isTrue();
    }

    private Long persistLimitedCoupon(int totalQuantity) {
        return couponRepository.saveAndFlush(Coupon.builder(
                        "선착순 한정", CouponDiscountType.FIXED_AMOUNT, 1_000, VALID_FROM, VALID_UNTIL)
                .totalQuantity(totalQuantity)
                .build())
                .getId();
    }

    private List<Long> createMembers(int count) {
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(Member.register(
                    "concurrency-" + i + "@example.com",
                    "$2a$10$dummyhashvalueforintegrationtest...",
                    "Member" + i,
                    String.format("010%08d", i)));
        }
        return memberRepository.saveAll(members).stream().map(Member::getId).toList();
    }

    /** ready 게이트로 동시 출발시키는 부하 하니스 (OversellingBaselineTest 패턴). */
    private void runConcurrently(int requests, java.util.function.IntConsumer task) throws InterruptedException {
        CountDownLatch readyGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(requests);
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        boolean settled;
        try {
            for (int i = 0; i < requests; i++) {
                final int index = i;
                pool.submit(() -> {
                    try {
                        readyGate.await();
                        task.accept(index);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            readyGate.countDown();
            settled = doneGate.await(DONE_GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
        assertThat(settled).as("동시 요청들이 timeout 안에 완료되어야 결과가 유효하다").isTrue();
    }
}
