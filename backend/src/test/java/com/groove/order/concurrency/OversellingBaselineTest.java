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
 * 주문 재고 차감 동시성 테스트 (#46 baseline · #205 비관적 락).
 *
 * <p>쿠폰 선착순 발급(#90, {@code CouponIssuanceConcurrencyTest})과 같은 서사다 — 베이스라인(락 없음)은
 * lost-update(오버셀)를 재현({@code @Disabled} 로 보존)하고, 비관적 락 경로({@code SELECT ... FOR UPDATE})는
 * 오버셀 0 을 증명한다. 두 시나리오가 한 클래스에서 Before/After 비교 자료를 만든다.
 *
 * <ul>
 *   <li>{@link #concurrentOrders_withoutLock_produceOversell} — 락 미적용
 *       ({@link OrderService#placeWithoutLock}) baseline. {@code @Disabled} 로 일반 CI 빌드에서 제외하며,
 *       시연 시 어노테이션을 일시 제거해 실행한 뒤 결과를
 *       {@code docs/troubleshooting/overselling-baseline.md} 에 캡처한다.</li>
 *   <li>{@link #concurrentOrders_withPessimisticLock_noOversell} — 비관적 락
 *       ({@link OrderService#place}) active 검증. created ≤ 재고, lost-update 0 을 단언하는 회귀 가드다 —
 *       누군가 {@code findByIdForUpdate} 락을 제거하면 즉시 실패한다.</li>
 * </ul>
 *
 * <p>동시 출발·지연 수집은 공용 {@link ConcurrencyHarness} 가 담당한다(쿠폰 테스트와 동일 하니스, TPS·p95 박제 →
 * docs/improvements/concurrency.md §4 근거).
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
    }

    @AfterEach
    void tearDown() {
        cleanupAll();
    }

    private void cleanupAll() {
        // FK 의존 순서대로 부모 repository 를 비운다 (CartOrderE2EIntegrationTest 와 동일 패턴).
        // cart 정리는 본 테스트에서 cart 를 만들지 않더라도, Testcontainers 컨테이너 재사용 환경에서
        // 외부 테스트가 남긴 cart 데이터가 album 삭제 시 FK 위반을 유발하는 경로를 차단한다.
        // refresh_token → member FK 도 먼저 정리 — 다른 테스트가 남긴 토큰이 member 삭제를 막지 않도록.
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
        long persistedOrders = orderRepository.count();
        logMeasurement("비관적", success, insufficient, other, finalStock, actualDecrement, persistedOrders, result);

        // 오버셀 0 의 증거 — 비관적 락(SELECT ... FOR UPDATE)이 read-modify-write 를 직렬화하면:
        //   success == 재고, finalStock == 0(음수 진입 없음), persistedOrders == 실제 차감량(lost-update 0).
        assertThat(success.get()).as("정확히 재고만큼만 주문 성공").isEqualTo(INITIAL_STOCK);
        assertThat(insufficient.get()).as("나머지는 재고부족(409)으로 거절").isEqualTo(CONCURRENT_REQUESTS - INITIAL_STOCK);
        assertThat(finalStock).as("최종 재고 == 0 (음수 진입 없음)").isZero();
        assertThat(persistedOrders).as("영속 주문 수 == 실제 차감량 (lost-update 0)").isEqualTo(actualDecrement);
        assertThat(persistedOrders).as("오버셀 0 — 영속 주문 ≤ 초기 재고").isLessThanOrEqualTo(INITIAL_STOCK);
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
                // 락 미적용 demo 경로 — 동시 read-modify-write 로 lost-update 를 노출한다.
                orderService.placeWithoutLock(memberId, request);
                success.incrementAndGet();
            } catch (InsufficientStockException ex) {
                insufficient.incrementAndGet();
            } catch (RuntimeException ex) {
                // 락 없는 동시 쓰기는 같은 album 행을 두고 락 경합/데드락을 일으켜 다수가
                // CannotAcquireLockException 으로 롤백된다. 커밋된 소수에서 lost-update 가 누적된다.
                other.incrementAndGet();
            }
        });

        int finalStock = albumRepository.findById(albumId).orElseThrow().getStock();
        int actualDecrement = INITIAL_STOCK - finalStock;
        long persistedOrders = orderRepository.count();
        logMeasurement("baseline", success, insufficient, other, finalStock, actualDecrement, persistedOrders, result);

        // 통과 조건 = "오버셀 증거". lost-update 시 다음 중 하나 이상이 성립한다:
        //   (1) successCount > INITIAL_STOCK    — 재고를 초과한 주문이 영속화 (이상적 오버셀 시나리오)
        //   (2) finalStock < 0                  — 마지막 write 가 음수 재고로 진입 (도메인 가드 우회)
        //   (3) persistedOrders > actualDecrement — 영속 주문 수가 실제 차감량을 초과 (lost-update 직접 증거)
        // MySQL InnoDB row lock + deadlock detection 으로 (1)/(2) 보다 (3) 이 두드러진다 — OR 로 검증.
        boolean oversold = success.get() > INITIAL_STOCK
                || finalStock < 0
                || persistedOrders > actualDecrement;
        assertThat(oversold)
                .as("오버셀 baseline — success(%d)>stock(%d) 또는 finalStock(%d)<0 또는 persistedOrders(%d)>actualDecrement(%d) 중 하나는 성립해야 한다",
                        success.get(), INITIAL_STOCK, finalStock, persistedOrders, actualDecrement)
                .isTrue();
    }

    private OrderCreateRequest singleAlbumOrder() {
        return new OrderCreateRequest(
                List.of(new OrderItemRequest(albumId, 1)),
                null,
                OrderFixtures.sampleShippingInfoRequest(),
                null);
    }

    /** 콘솔 박제용 측정 로그 — 정확성 카운트 + 처리량(elapsedMs·TPS·p95) 한 줄. (overselling-baseline.md §3.1 포맷) */
    private void logMeasurement(String label, AtomicInteger success, AtomicInteger insufficient, AtomicInteger other,
                                int finalStock, int actualDecrement, long persistedOrders, LoadResult result) {
        log.info("[#205 {}] success={}, insufficient={}, other={}, finalStock={}, actualDecrement={}, persistedOrders={}"
                        + " | elapsedMs={}, tps={}, p95Ms={}",
                label, success.get(), insufficient.get(), other.get(), finalStock, actualDecrement, persistedOrders,
                result.elapsedMs(), String.format("%.1f", result.tps()), result.p95Millis());
    }
}
