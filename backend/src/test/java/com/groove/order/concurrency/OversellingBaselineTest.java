package com.groove.order.concurrency;

import com.groove.auth.domain.RefreshTokenRepository;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 재고 차감 동시성 테스트. 락 미적용 baseline(oversell, @Disabled) 대비 비관적 락이 created ≤ 재고로
 * lost-update 0 을 보장하고, 단일 재고(stock=1)에 동시 100 주문이 정확히 1건만 성공함을 검증한다.
 * 동시 출발·지연 수집은 ConcurrencyHarness 가 담당한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("주문 재고 차감 동시성 (#46 baseline · #205 비관적 락)")
class OversellingBaselineTest {

    private static final Logger log = LoggerFactory.getLogger(OversellingBaselineTest.class);

    private static final int INITIAL_STOCK = 100;
    private static final int CONCURRENT_REQUESTS = 200;
    private static final int THREAD_POOL_SIZE = 64;

    // 단일 재고(stock=1) — 1장짜리 희귀반에 동시 100 주문 → 정확히 1 성공.
    private static final int SINGLE_STOCK = 1;
    private static final int SINGLE_STOCK_REQUESTS = 100;

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

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Long albumId;
    private Long rarityAlbumId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        cleanupAll();

        Member member = memberRepository.saveAndFlush(
                MemberFixtures.register("oversell@example.com",
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

        // 단일 재고(stock=1) 희귀반 — 같은 artist/genre/label 재사용.
        rarityAlbumId = albumRepository.saveAndFlush(
                Album.create("Single Stock Rarity", artist, genre, label,
                        (short) 2026, AlbumFormat.LP_12, 10_000L, SINGLE_STOCK,
                        AlbumStatus.SELLING, false, null, null)).getId();
    }

    @AfterEach
    void tearDown() {
        cleanupAll();
    }

    private void cleanupAll() {
        // FK 의존 순서대로 부모 repository 를 비운다.
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
    @DisplayName("비관적 락 — 재고 100 / 동시 200 주문 → 정확히 100 성공, 오버셀 0 (lost-update 0)")
    void concurrentOrders_withPessimisticLock_noOversell() throws InterruptedException {
        OrderCreateRequest request = singleAlbumOrder();

        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        LoadResult result = ConcurrencyHarness.runConcurrently(THREAD_POOL_SIZE, CONCURRENT_REQUESTS, i -> {
            try {
                orderService.place(memberId, request);
                success.incrementAndGet();
            } catch (InsufficientStockException ex) {
                insufficient.incrementAndGet();
            } catch (RuntimeException ex) {
                other.incrementAndGet();
                log.warn("예상 외 예외", ex);
            }
        });

        int finalStock = albumRepository.findById(albumId).orElseThrow().getStock();
        int actualDecrement = INITIAL_STOCK - finalStock;
        long persistedOrders = orderRepository.countByMemberId(memberId);
        logMeasurement("비관적", success, insufficient, other, finalStock, actualDecrement, persistedOrders, result);

        // success == 재고, finalStock == 0, persistedOrders == 실제 차감량.
        assertThat(success.get()).as("정확히 재고만큼만 주문 성공").isEqualTo(INITIAL_STOCK);
        assertThat(insufficient.get()).as("나머지는 재고부족(409)으로 거절").isEqualTo(CONCURRENT_REQUESTS - INITIAL_STOCK);
        assertThat(finalStock).as("최종 재고 == 0 (음수 진입 없음)").isZero();
        assertThat(persistedOrders).as("영속 주문 수 == 실제 차감량 (lost-update 0)").isEqualTo(actualDecrement);
        assertThat(persistedOrders).as("오버셀 0 — 영속 주문 ≤ 초기 재고").isLessThanOrEqualTo(INITIAL_STOCK);
        assertThat(other.get()).as("FOR UPDATE 직렬화로 deadlock 폭주 없음").isZero();
    }

    @Test
    @DisplayName("비관적 락 — 단일 재고(stock=1) 희귀반 / 동시 100 주문 → 정확히 1 성공, 99 재고부족, 오버셀 0 (#209)")
    void concurrentOrders_singleStockRarity_exactlyOneSucceeds() throws InterruptedException {
        // 1장짜리 희귀반을 100명이 동시에 집어도 정확히 1명만 성공해야 한다.
        OrderCreateRequest request = singleAlbumOrder(rarityAlbumId);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        LoadResult result = ConcurrencyHarness.runConcurrently(THREAD_POOL_SIZE, SINGLE_STOCK_REQUESTS, i -> {
            try {
                orderService.place(memberId, request);
                success.incrementAndGet();
            } catch (InsufficientStockException ex) {
                insufficient.incrementAndGet();
            } catch (RuntimeException ex) {
                other.incrementAndGet();
                log.warn("예상 외 예외", ex);
            }
        });

        int finalStock = albumRepository.findById(rarityAlbumId).orElseThrow().getStock();
        int actualDecrement = SINGLE_STOCK - finalStock;
        long persistedOrders = orderRepository.countByMemberId(memberId);
        logMeasurement("단일재고", success, insufficient, other, finalStock, actualDecrement, persistedOrders, result);

        // 단일 행 FOR UPDATE 가 100 트랜잭션을 직렬화 → 1건만 차감·commit, 나머지 99건은 거절.
        assertThat(success.get()).as("정확히 1건만 성공").isEqualTo(SINGLE_STOCK);
        assertThat(insufficient.get()).as("나머지 99건은 재고부족(409)으로 거절").isEqualTo(SINGLE_STOCK_REQUESTS - SINGLE_STOCK);
        assertThat(finalStock).as("최종 재고 == 0 (음수 진입 없음)").isZero();
        assertThat(persistedOrders).as("영속 주문 수 == 실제 차감량 (lost-update 0)").isEqualTo(actualDecrement);
        assertThat(persistedOrders).as("오버셀 0 — 영속 주문 ≤ 1").isLessThanOrEqualTo(SINGLE_STOCK);
        assertThat(other.get()).as("FOR UPDATE 직렬화로 deadlock 폭주 없음").isZero();
    }

    @Test
    @Disabled("오버셀 baseline 시연용 — 락 없는 read-modify-write 의 결함 재현. 일반 CI 빌드에서는 실행하지 않는다 (#46)")
    @DisplayName("베이스라인(락 없음) — 재고 100 / 동시 200 주문 → lost-update 로 오버셀 재현")
    void concurrentOrders_withoutLock_produceOversell() throws InterruptedException {
        OrderCreateRequest request = singleAlbumOrder();

        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        LoadResult result = ConcurrencyHarness.runConcurrently(THREAD_POOL_SIZE, CONCURRENT_REQUESTS, i -> {
            try {
                // 락 미적용 demo 경로 — 동시 read-modify-write.
                orderService.placeWithoutLock(memberId, request);
                success.incrementAndGet();
            } catch (InsufficientStockException ex) {
                insufficient.incrementAndGet();
            } catch (RuntimeException ex) {
                other.incrementAndGet();
            }
        });

        int finalStock = albumRepository.findById(albumId).orElseThrow().getStock();
        int actualDecrement = INITIAL_STOCK - finalStock;
        long persistedOrders = orderRepository.countByMemberId(memberId);
        logMeasurement("baseline", success, insufficient, other, finalStock, actualDecrement, persistedOrders, result);

        // 오버셀 증거 — 다음 중 하나 이상이 성립한다:
        //   (1) successCount > INITIAL_STOCK
        //   (2) finalStock < 0
        //   (3) persistedOrders > actualDecrement
        boolean oversold = success.get() > INITIAL_STOCK
                || finalStock < 0
                || persistedOrders > actualDecrement;
        assertThat(oversold)
                .as("오버셀 baseline — success(%d)>stock(%d) 또는 finalStock(%d)<0 또는 persistedOrders(%d)>actualDecrement(%d) 중 하나는 성립해야 한다",
                        success.get(), INITIAL_STOCK, finalStock, persistedOrders, actualDecrement)
                .isTrue();
    }

    private OrderCreateRequest singleAlbumOrder() {
        return singleAlbumOrder(albumId);
    }

    private OrderCreateRequest singleAlbumOrder(Long targetAlbumId) {
        return new OrderCreateRequest(
                List.of(new OrderItemRequest(targetAlbumId, 1)),
                null,
                OrderFixtures.sampleShippingInfoRequest(),
                null);
    }

    /** 측정 로그 — 정확성 카운트 + 처리량(elapsedMs·TPS·p95) 한 줄. */
    private void logMeasurement(String label, AtomicInteger success, AtomicInteger insufficient, AtomicInteger other,
                                int finalStock, int actualDecrement, long persistedOrders, LoadResult result) {
        log.info("[#205 {}] success={}, insufficient={}, other={}, finalStock={}, actualDecrement={}, persistedOrders={}"
                        + " | elapsedMs={}, tps={}, p95Ms={}",
                label, success.get(), insufficient.get(), other.get(), finalStock, actualDecrement, persistedOrders,
                result.elapsedMs(), String.format("%.1f", result.tps()), result.p95Millis());
    }
}
