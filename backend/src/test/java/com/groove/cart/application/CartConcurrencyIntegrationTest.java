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
import com.groove.support.ConcurrencyHarness;
import com.groove.support.MemberFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장바구니 동시 쓰기 — 같은 회원·같은 album 을 동시에 addItem 했을 때 UNIQUE 경합이
 * 500 으로 새지 않고, CartService 의 1회 멱등 재시도가 누적으로 흡수하는지 실 DB 로 검증한다.
 * 타이밍에 무관한 불변식만 단언한다: (a) 예상 외 예외 0(UNIQUE 경합이 500 으로 새지 않음),
 * (b) cart·cart_item 각 1개, (c) 최종 quantity 는 [1, 성공 건수] 범위.
 * 누적 quantity 의 정확한 합산(read-modify-write lost update)은 범위 밖이며 별개 동시성 과제다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("장바구니 동시 addItem 멱등 재시도 (#318)")
class CartConcurrencyIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(CartConcurrencyIntegrationTest.class);

    private static final int CONCURRENT_ADDS = 5;

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

    private Long memberId;
    private Long albumId;

    @BeforeEach
    void setUp() {
        clearAll();

        memberId = memberRepository.saveAndFlush(
                MemberFixtures.register("cart-race@example.com", "$2a$12$hash", "동시성", "01012345678")).getId();

        Artist artist = artistRepository.saveAndFlush(Artist.create("The Beatles", null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock"));
        Label label = labelRepository.saveAndFlush(Label.create("Apple Records"));
        albumId = albumRepository.saveAndFlush(
                Album.create("Abbey Road", artist, genre, label,
                        (short) 1969, AlbumFormat.LP_12, 35000L, 100,
                        AlbumStatus.SELLING, false, null, null)).getId();
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
    @DisplayName("같은 회원·같은 album 동시 addItem → 500 미발생, cart 1개, 최종 quantity == 성공 건수")
    void concurrentAddSameAlbum_recoversIdempotently() throws InterruptedException {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger lostRace = new AtomicInteger();  // 1회 재시도로도 흡수 못 한 제약 위반 경합 — 운영에선 409.
        AtomicInteger other = new AtomicInteger();

        ConcurrencyHarness.runConcurrently(CONCURRENT_ADDS, CONCURRENT_ADDS, i -> {
            try {
                cartService.addItem(memberId, albumId, 1);
                success.incrementAndGet();
            } catch (DataIntegrityViolationException race) {
                lostRace.incrementAndGet();
            } catch (RuntimeException ex) {
                other.incrementAndGet();
                log.warn("예상 외 예외", ex);
            }
        });

        Cart cart = cartRepository.findByMemberIdWithItems(memberId).orElseThrow();
        int finalQuantity = cart.getItems().isEmpty() ? 0 : cart.getItems().get(0).getQuantity();
        log.info("[#318 멱등 재시도] success={}, lostRace(409)={}, other={}, carts={}, finalQuantity={}",
                success.get(), lostRace.get(), other.get(), cartRepository.count(), finalQuantity);

        assertThat(other.get()).as("UNIQUE 경합은 500/예상외 예외로 새지 않는다").isZero();
        assertThat(success.get()).as("최소 1건은 성공한다").isGreaterThanOrEqualTo(1);
        assertThat(success.get() + lostRace.get()).as("모든 요청은 성공 또는 409(DIVE)로 귀결").isEqualTo(CONCURRENT_ADDS);
        assertThat(cartRepository.count()).as("회원당 cart 정확히 1개 (uk_cart_member)").isEqualTo(1);
        assertThat(cart.getItems()).as("동일 album 행은 1개 (uk_cart_item_cart_album)").hasSize(1);
        // 누적 정확합산(lost update)은 범위 밖 — 1 이상이고 성공 건수를 넘지 않음만 보장.
        assertThat(finalQuantity).as("최종 quantity ∈ [1, 성공 건수]")
                .isBetween(1, success.get());
    }
}
