package com.groove.cart.application;

import com.groove.auth.domain.RefreshTokenRepository;
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
import com.groove.cart.domain.Cart;
import com.groove.cart.domain.CartRepository;
import com.groove.member.domain.MemberRepository;
import com.groove.support.MemberFixtures;
import com.groove.support.TestcontainersConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// album N+1 제거 회귀 가드.
// GET /api/cart (CartService.find) 가 cart_item.album(LAZY)을 Album 클래스 @BatchSize 로 IN 일괄 페치하고,
// album.artist 는 Artist 클래스 @BatchSize 로 흡수해 항목 수와 무관하게 쿼리 수가 상수임을 Statistics 로 고정한다.
// 판정 기준: prepareStatementCount == 3 (cart+items 1 / album IN 배치 1 / artist IN 배치 1).
// Album 클래스의 @BatchSize 를 제거하면 album 이 항목당 SELECT 로 풀려 이 어서션이 깨진다.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("CartService.find — album N+1 제거 회귀 가드 (#318)")
class CartQueryN1Test {

    // cart+items(1) + album IN 배치(1) + artist IN 배치(1) = 상수.
    private static final long EXPECTED_QUERY_COUNT = 3L;

    @Autowired
    private CartService cartService;
    @Autowired
    private CartRepository cartRepository;
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
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;
    private Long memberId;

    @BeforeEach
    void setUp() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @AfterEach
    void tearDown() {
        // 공유 DB 오염 방지 — cart_item 잔존이 다른 테스트의 album 삭제(fk_cart_item_album RESTRICT)를 막지 않도록 정리.
        clearAll();
    }

    private void clearAll() {
        // FK 정리 순서: refresh_token, cart(CASCADE → cart_item) → album → artist/genre/label → member.
        refreshTokenRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("5개 항목 cart 조회 — album/artist 가 IN 배치로 페치돼 쿼리 3개 (항목당 SELECT 없음)")
    void find_batchFetchesAlbums_noN1() {
        seedCartWithItems(5);

        Cart cart = cartService.find(memberId);

        assertThat(cart.getItems()).hasSize(5);
        assertThat(statistics.getPrepareStatementCount())
                .as("Album 클래스 @BatchSize 를 제거하면 album 이 항목당 SELECT 로 풀려 깨진다 — N+1 재발 가드")
                .isEqualTo(EXPECTED_QUERY_COUNT);
        // 연관 값이 프록시 초기화돼 트랜잭션 밖 직렬화에도 안전
        cart.getItems().forEach(item -> {
            assertThat(item.getAlbum().getTitle()).isNotNull();
            assertThat(item.getAlbum().getArtist().getName()).isNotNull();
        });
    }

    @Test
    @DisplayName("[#318] 항목 수가 달라도 쿼리 수 상수 — 3개·7개 모두 3개")
    void find_keepsQueryCountConstant_regardlessOfItemCount() {
        long count3 = measureQueryCountForItems(3);
        long count7 = measureQueryCountForItems(7);

        System.out.printf("[#318 N+1 after] items=3 → prepareStatementCount=%d, items=7 → prepareStatementCount=%d%n",
                count3, count7);

        assertThat(count3)
                .as("항목 수가 늘어도 쿼리 수가 선형 증가하지 않아야 한다 (베이스라인 1+N+1 → 상수 3)")
                .isEqualTo(count7)
                .isEqualTo(EXPECTED_QUERY_COUNT);
    }

    private long measureQueryCountForItems(int items) {
        seedCartWithItems(items);

        Cart cart = cartService.find(memberId);

        assertThat(cart.getItems()).hasSize(items);
        return statistics.getPrepareStatementCount();
    }

    // 전체 wipe 후 distinct album 을 가진 항목 count 개로 cart 를 적재하고, 통계/영속성 컨텍스트를 비워
    // 측정 대상인 find 호출만 통계에 잡히도록 한다.
    private void seedCartWithItems(int count) {
        clearAll();

        memberId = memberRepository.saveAndFlush(
                MemberFixtures.register("cart-n1@example.com", "$2a$12$hash", "엔플원", "01012345678")).getId();

        for (int i = 0; i < count; i++) {
            Artist artist = artistRepository.saveAndFlush(Artist.create("Artist " + i, null));
            Genre genre = genreRepository.saveAndFlush(Genre.create("Genre " + i));
            Label label = labelRepository.saveAndFlush(Label.create("Label " + i));
            Long albumId = albumRepository.saveAndFlush(Album.create(
                    "Album " + i, artist, genre, label,
                    (short) (1965 + i), AlbumFormat.LP_12, 30000L, 10,
                    AlbumStatus.SELLING, false, null, null)).getId();
            cartService.addItem(memberId, albumId, 1);
        }

        entityManager.clear();
        statistics.clear();
    }
}
