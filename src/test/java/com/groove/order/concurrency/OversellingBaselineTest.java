package com.groove.order.concurrency;

import com.groove.cart.domain.CartRepository;
import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.order.api.dto.OrderCreateRequest;
import com.groove.order.api.dto.OrderItemRequest;
import com.groove.order.application.OrderService;
import com.groove.order.domain.OrderRepository;
import com.groove.order.exception.InsufficientStockException;
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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오버셀 baseline 재현 테스트 (#46, W6-6).
 *
 * <p>의도: 락 없이 구현된 {@link OrderService#place} 의 재고 차감이 동시 주문 시
 * lost-update / oversell 을 일으키는 것을 측정 가능한 형태로 보존한다 (W10-3 비관적 락 적용 후
 * 같은 시나리오를 재실행해 Before/After 비교 자료를 만든다).
 *
 * <p>본 테스트는 일반 빌드에서 실행되지 않도록 클래스 레벨 {@link Disabled} 로 격리된다.
 * 시연 시 {@link Disabled} 만 일시적으로 제거하고 실행한 뒤 결과를
 * {@code docs/troubleshooting/overselling-baseline.md} 에 캡처해 보존한다.
 *
 * <p>"수정"이 아니라 "기록"이 목적인 테스트이므로 assertion 의 의미는 통상의 "기능 통과"가 아니라
 * "오버셀 발생을 증명" 한다는 점에 있다 — 다음 세 시그널 중 하나라도 성립해야 baseline 으로 유효하다:
 * <ul>
 *   <li>{@code successCount > INITIAL_STOCK} — 재고를 초과한 주문이 영속화 (이상적 시나리오)</li>
 *   <li>{@code finalStock < 0} — 마지막 write 가 음수로 진입 (도메인 가드 우회)</li>
 *   <li>{@code persistedOrders > actualDecrement} — 영속 주문 수가 실제 차감량 초과
 *       (MySQL InnoDB row lock + deadlock detection 환경에서 가장 두드러지는 시그널)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("오버셀 baseline (#46) — 락 없는 재고 차감의 동시성 결함 재현")
@Disabled("W10-3 비관적 락 적용 후 활성화 — 본 테스트는 락 미적용 상태의 baseline 시연용이며 일반 CI 빌드에서는 실행하지 않는다 (#46)")
class OversellingBaselineTest {

    private static final Logger log = LoggerFactory.getLogger(OversellingBaselineTest.class);

    private static final int INITIAL_STOCK = 100;
    private static final int CONCURRENT_REQUESTS = 200;
    private static final int THREAD_POOL_SIZE = 64;
    private static final long DONE_GATE_TIMEOUT_SECONDS = 60L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    private Long albumId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        cleanupAll();

        Member member = memberRepository.saveAndFlush(
                Member.register("oversell@example.com",
                        "$2a$10$dummyhashvalueforintegrationtest...",
                        "Tester", "01000000000"));
        memberId = member.getId();

        Artist artist = artistRepository.saveAndFlush(Artist.create("Race Conditioners", null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Concurrency"));
        Label label = labelRepository.saveAndFlush(Label.create("Lost Update Records"));

        albumId = albumRepository.saveAndFlush(
                Album.create("Oversell Baseline", artist, genre, label,
                        (short) 2026, AlbumFormat.LP_12, 10_000L, INITIAL_STOCK,
                        AlbumStatus.SELLING, false, null, null)).getId();
    }

    @AfterEach
    void tearDown() {
        cleanupAll();
    }

    private void cleanupAll() {
        // FK 의존 순서대로 부모 repository 를 비운다 (CartOrderE2EIntegrationTest 와 동일 패턴).
        // cart 정리는 본 테스트에서 cart 를 만들지 않더라도, Testcontainers 컨테이너 재사용 환경에서
        // 외부 테스트가 남긴 cart 데이터가 album 삭제 시 FK 위반을 유발하는 경로를 차단한다.
        cartRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("재고 100짜리 앨범에 동시 200건 주문 → 락 없는 차감으로 오버셀 발생")
    void concurrentOrders_withoutLock_produceOversell() throws InterruptedException {
        CountDownLatch readyGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(CONCURRENT_REQUESTS);
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger insufficientCount = new AtomicInteger();
        AtomicInteger otherFailureCount = new AtomicInteger();

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(albumId, 1)),
                null,
                com.groove.support.OrderFixtures.sampleShippingInfoRequest());

        boolean settled;
        long elapsedMs;
        try {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                pool.submit(() -> {
                    try {
                        readyGate.await();
                        orderService.place(memberId, request);
                        successCount.incrementAndGet();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        otherFailureCount.incrementAndGet();
                    } catch (InsufficientStockException ex) {
                        insufficientCount.incrementAndGet();
                    } catch (Throwable ex) {
                        otherFailureCount.incrementAndGet();
                        log.warn("예상 외 예외", ex);
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            long startNanos = System.nanoTime();
            readyGate.countDown();
            settled = doneGate.await(DONE_GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        } finally {
            // doneGate.await() 가 InterruptedException 을 던지거나 submit 단계에서 예외가 날 경우에도
            // ExecutorService 가 반드시 종료되도록 finally 로 감싼다 (CodeRabbit 리뷰 반영).
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }

        int finalStock = albumRepository.findById(albumId).orElseThrow().getStock();
        int actualDecrement = INITIAL_STOCK - finalStock;
        long persistedOrders = orderRepository.count();

        log.info("[#46 오버셀 baseline] initialStock={}, concurrentRequests={}, threadPool={}, elapsedMs={}",
                INITIAL_STOCK, CONCURRENT_REQUESTS, THREAD_POOL_SIZE, elapsedMs);
        log.info("[#46 오버셀 baseline] success={}, insufficient={}, other={}, finalStock={}, actualDecrement={}, persistedOrders={}",
                successCount.get(), insufficientCount.get(), otherFailureCount.get(),
                finalStock, actualDecrement, persistedOrders);

        assertThat(settled)
                .as("동시 요청들이 timeout 안에 완료되어야 결과가 유효하다")
                .isTrue();

        // 본 테스트의 통과 조건 = "오버셀 증거" 다.
        // 락 없는 read-modify-write 구간에서 lost-update 가 발생하면 다음 시그널 중
        // 하나 이상이 반드시 나타난다:
        //   (1) successCount > INITIAL_STOCK
        //       — 재고보다 많은 주문이 영속화됨 (이상적 오버셀 시나리오)
        //   (2) finalStock < 0
        //       — 마지막 write 가 음수로 진입 (도메인 가드를 우회한 경우)
        //   (3) persistedOrders > actualDecrement
        //       — 영속된 주문 수가 실제 재고 차감량을 초과 (락 없는 lost-update 의 직접 증거)
        // MySQL InnoDB 의 row-level lock + deadlock detection 으로 (1)/(2) 보다는 (3) 이
        // 두드러질 수 있다 (deadlock 으로 일부 TX 가 롤백되지만, 살아남은 TX 들 사이에서는
        // lost-update 가 누적된다). OS 스케줄링·JPA flush 타이밍에 따라 비결정적이므로 OR 로 검증.
        boolean oversold = successCount.get() > INITIAL_STOCK
                || finalStock < 0
                || persistedOrders > actualDecrement;
        assertThat(oversold)
                .as("오버셀 baseline — success(%d)>stock(%d) 또는 finalStock(%d)<0 또는 persistedOrders(%d)>actualDecrement(%d) 중 하나는 성립해야 한다",
                        successCount.get(), INITIAL_STOCK, finalStock,
                        persistedOrders, actualDecrement)
                .isTrue();
    }
}
