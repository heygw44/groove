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
import com.groove.cart.domain.CartRepository;
import com.groove.member.domain.MemberRepository;
import com.groove.support.MemberFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원 탈퇴 장바구니 정리의 cascade 검증. CartService.deleteForMember 가 아이템이 담긴 cart 를 삭제할 때
 * cart_item 까지 함께 제거되는지 실 DB 로 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("회원 탈퇴 장바구니 cascade 정리 (CartService.deleteForMember)")
class CartWithdrawalCleanupIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long albumId;

    @BeforeEach
    void setUp() {
        // FK 정리 순서: refresh_token, cart(CASCADE → cart_item) → album → artist/genre/label → member.
        refreshTokenRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        memberId = memberRepository.saveAndFlush(
                MemberFixtures.register("cart-withdraw@example.com", "$2a$12$hash", "장바구니", "01012345678")).getId();

        Artist artist = artistRepository.saveAndFlush(Artist.create("The Beatles", null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock"));
        Label label = labelRepository.saveAndFlush(Label.create("Apple Records"));
        albumId = albumRepository.saveAndFlush(
                Album.create("Abbey Road", artist, genre, label,
                        (short) 1969, AlbumFormat.LP_12, 35000L, 10,
                        AlbumStatus.SELLING, false, null, null)).getId();
    }

    private int cartItemCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cart_item", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("아이템이 담긴 장바구니 → deleteForMember 시 cart 와 cart_item 모두 삭제 (cascade)")
    void deleteForMember_withItems_cascadesToCartItems() {
        cartService.addItem(memberId, albumId, 2);
        assertThat(cartRepository.findByMemberId(memberId)).isPresent();
        assertThat(cartItemCount()).isEqualTo(1);

        cartService.deleteForMember(memberId);

        assertThat(cartRepository.findByMemberId(memberId)).isEmpty();
        assertThat(cartItemCount()).isZero();
    }
}
