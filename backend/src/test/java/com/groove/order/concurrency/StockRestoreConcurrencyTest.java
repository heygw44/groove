package com.groove.order.concurrency;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.cart.domain.CartRepository;
import com.groove.catalog.album.application.AlbumService;
import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.album.exception.IllegalStockAdjustmentException;
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
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.exception.InsufficientStockException;
import com.groove.support.ConcurrencyHarness;
import com.groove.support.ConcurrencyHarness.LoadResult;
import com.groove.support.MemberFixtures;
import com.groove.support.OrderFixtures;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재고 복원 경로 동시성. 같은 album 행에 place(−1)·cancel(+1)을 인터리브해 원자적 복원이 lost-update 없이
 * finalStock 을 맞추는지, 재고 1 에 동시 admin 조정 2건이 1 성공/1 거부로 음수에 안 빠지는지 검증한다.
 * 락 없는 RMW 복원 baseline 은 @Disabled.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("재고 복원 경로 동시성 (#234 원자적 가산 UPDATE)")
class StockRestoreConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(StockRestoreConcurrencyTest.class);

    private static final int INITIAL_STOCK = 200;
    private static final int PAIRS = 50; // place N + cancel N = 2N 동시 작업
    private static final int THREAD_POOL_SIZE = 64;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AlbumService albumService;

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

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long albumId;
    private Long singleStockAlbumId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        cleanupAll();

        Member member = memberRepository.saveAndFlush(
                MemberFixtures.register("stock-restore@example.com",
                        "$2a$10$dummyhashvalueforintegrationtest...",
                        "Tester", "01000000000"));
        memberId = member.getId();

        Artist artist = artistRepository.saveAndFlush(Artist.create("Race Conditioners", null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Concurrency"));
        Label label = labelRepository.saveAndFlush(Label.create("Lost Update Records"));

        albumId = albumRepository.saveAndFlush(
                Album.create("Restore Concurrency", artist, genre, label,
                        (short) 2026, AlbumFormat.LP_12, 10_000L, INITIAL_STOCK,
                        AlbumStatus.SELLING, false, null, null)).getId();

        // admin 음수 가드 동시성 경계용 — 재고 1.
        singleStockAlbumId = albumRepository.saveAndFlush(
                Album.create("Single Stock", artist, genre, label,
                        (short) 2026, AlbumFormat.LP_12, 10_000L, 1,
                        AlbumStatus.SELLING, false, null, null)).getId();
    }

    @AfterEach
    void tearDown() {
        cleanupAll();
    }

    private void cleanupAll() {
        refreshTokenRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("원자적 복원 — 동시 place(−1)·cancel(+1) 인터리브 → lost-update 0 (finalStock == 기대치)")
    void concurrentPlaceAndCancel_atomicRestore_noLostUpdate() throws InterruptedException {
        // 선행: PENDING 주문 N 건(각 −1) → 재고 = INITIAL_STOCK − N.
        List<String> orderNumbers = new ArrayList<>(PAIRS);
        for (int i = 0; i < PAIRS; i++) {
            Order seeded = orderService.place(memberId, singleAlbumOrder());
            orderNumbers.add(seeded.getOrderNumber());
        }
        int stockAfterSeed = albumRepository.findById(albumId).orElseThrow().getStock();
        assertThat(stockAfterSeed).isEqualTo(INITIAL_STOCK - PAIRS);

        AtomicInteger placeSuccess = new AtomicInteger();
        AtomicInteger cancelSuccess = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        // 동시: 짝수 = place(−1), 홀수 = 선행 주문 cancel(+1).
        LoadResult result = ConcurrencyHarness.runConcurrently(THREAD_POOL_SIZE, 2 * PAIRS, i -> {
            try {
                if (i % 2 == 0) {
                    orderService.place(memberId, singleAlbumOrder());
                    placeSuccess.incrementAndGet();
                } else {
                    orderService.cancel(memberId, orderNumbers.get(i / 2), "동시성 테스트");
                    cancelSuccess.incrementAndGet();
                }
            } catch (InsufficientStockException ex) {
                other.incrementAndGet();
            } catch (RuntimeException ex) {
                other.incrementAndGet();
                log.warn("예상 외 예외", ex);
            }
        });

        int finalStock = albumRepository.findById(albumId).orElseThrow().getStock();
        int expected = stockAfterSeed - placeSuccess.get() + cancelSuccess.get();
        log.info("[#234 원자적] placeSuccess={}, cancelSuccess={}, other={}, stockAfterSeed={}, finalStock={}, expected={}"
                        + " | elapsedMs={}, tps={}, p95Ms={}",
                placeSuccess.get(), cancelSuccess.get(), other.get(), stockAfterSeed, finalStock, expected,
                result.elapsedMs(), String.format("%.1f", result.tps()), result.p95Millis());

        assertThat(finalStock).as("lost-update 0 — 최종 재고 == 시드후재고 − 성공한 place + 성공한 cancel").isEqualTo(expected);
        assertThat(finalStock).as("음수 재고 미진입").isGreaterThanOrEqualTo(0);
        assertThat(other.get()).as("충분한 초기 재고 → 재고부족/예외 0").isZero();
        assertThat(placeSuccess.get()).as("모든 place 성공").isEqualTo(PAIRS);
        assertThat(cancelSuccess.get()).as("모든 cancel 성공").isEqualTo(PAIRS);
    }

    @Test
    @DisplayName("admin 재고조정 음수 가드 — 재고 1 에 동시 −1 두 건 → 정확히 1 성공/1 거부, 최종 0, 음수 미진입")
    void concurrentAdminAdjust_singleStock_negativeGuard() throws InterruptedException {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        ConcurrencyHarness.runConcurrently(2, 2, i -> {
            try {
                albumService.adjustStock(singleStockAlbumId, -1);
                success.incrementAndGet();
            } catch (IllegalStockAdjustmentException ex) {
                rejected.incrementAndGet();
            } catch (RuntimeException ex) {
                other.incrementAndGet();
                log.warn("예상 외 예외", ex);
            }
        });

        int finalStock = albumRepository.findById(singleStockAlbumId).orElseThrow().getStock();
        log.info("[#234 음수가드] success={}, rejected={}, other={}, finalStock={}",
                success.get(), rejected.get(), other.get(), finalStock);

        // FOR-UPDATE 직렬화: 첫 트랜잭션 1→0, 둘째는 0 을 읽고 음수 가드로 거절.
        assertThat(success.get()).as("정확히 1건 성공").isEqualTo(1);
        assertThat(rejected.get()).as("나머지 1건은 음수 가드로 거부").isEqualTo(1);
        assertThat(finalStock).as("최종 재고 == 0 (음수 미진입)").isZero();
        assertThat(other.get()).as("deadlock/기타 예외 0").isZero();
    }

    @Test
    @Disabled("복원 lost-update baseline 재현용 — 락 없는 RMW 복원의 결함 재현. 일반 CI 빌드에서는 실행하지 않는다 (#234)")
    @DisplayName("베이스라인(RMW 복원) — 동시 placeWithoutLock(−1)·RMW 복원(+1) → lost-update 재현")
    void concurrentPlaceAndRmwRestore_baseline_producesLostUpdate() throws InterruptedException {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int initialStock = albumRepository.findById(albumId).orElseThrow().getStock();

        AtomicInteger placeSuccess = new AtomicInteger();
        AtomicInteger restoreSuccess = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        LoadResult result = ConcurrencyHarness.runConcurrently(THREAD_POOL_SIZE, 2 * PAIRS, i -> {
            try {
                if (i % 2 == 0) {
                    orderService.placeWithoutLock(memberId, singleAlbumOrder()); // 락 없는 RMW 차감
                    placeSuccess.incrementAndGet();
                } else {
                    // 락 없는 RMW 복원 — findById 후 in-memory adjustStock, 커밋 시 dirty-check 절대값 UPDATE.
                    tx.executeWithoutResult(s ->
                            albumRepository.findById(albumId).orElseThrow().adjustStock(1));
                    restoreSuccess.incrementAndGet();
                }
            } catch (RuntimeException ex) {
                other.incrementAndGet(); // 락 경합/데드락 롤백
            }
        });

        int finalStock = albumRepository.findById(albumId).orElseThrow().getStock();
        int expected = initialStock - placeSuccess.get() + restoreSuccess.get();
        log.info("[#234 baseline] placeSuccess={}, restoreSuccess={}, other={}, initialStock={}, finalStock={}, expected={}"
                        + " | elapsedMs={}, tps={}, p95Ms={}",
                placeSuccess.get(), restoreSuccess.get(), other.get(), initialStock, finalStock, expected,
                result.elapsedMs(), String.format("%.1f", result.tps()), result.p95Millis());

        // 결함 증거: lost-update(finalStock ≠ 기대치) 또는 락 경합 롤백(other > 0).
        boolean defective = finalStock != expected || other.get() > 0;
        assertThat(defective)
                .as("복원 baseline — finalStock(%d) ≠ expected(%d) 이거나 롤백 other(%d) > 0 중 하나는 성립",
                        finalStock, expected, other.get())
                .isTrue();
    }

    private OrderCreateRequest singleAlbumOrder() {
        return new OrderCreateRequest(
                List.of(new OrderItemRequest(albumId, 1)),
                null,
                OrderFixtures.sampleShippingInfoRequest(),
                null);
    }
}
