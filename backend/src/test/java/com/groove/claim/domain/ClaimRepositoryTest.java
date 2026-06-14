package com.groove.claim.domain;

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
import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.support.MemberFixtures;
import com.groove.support.OrderFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("ClaimRepository 통합 테스트 (Testcontainers MySQL)")
class ClaimRepositoryTest {

    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private OrderRepository orderRepository;
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

    private Member member;
    private Album album;
    private int seq;

    @BeforeEach
    void setUp() {
        member = memberRepository.saveAndFlush(
                MemberFixtures.register("claim-repo-test@example.com", "$2a$12$hash", "반품러", "01012345678"));
        String uniq = "-" + System.nanoTime();
        Artist artist = artistRepository.saveAndFlush(Artist.create("Artist" + uniq, "desc"));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock" + uniq));
        Label label = labelRepository.saveAndFlush(Label.create("Label" + uniq));
        album = albumRepository.saveAndFlush(Album.create("Album", artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, 15_000L, 100, AlbumStatus.SELLING, false, null, null));
    }

    private Order persistOrder(int qty) {
        Order order = Order.placeForMember(
                "ORD-CLM-" + (++seq) + "-" + System.nanoTime(), member.getId(), OrderFixtures.sampleShippingInfo());
        order.addItem(OrderItem.create(album, qty));
        return orderRepository.saveAndFlush(order);
    }

    private Claim persistClaim(Order order, int returnQty) {
        Claim claim = Claim.request(order, "변심");
        claim.addItem(ClaimItem.of(order.getItems().get(0), returnQty));
        return claimRepository.saveAndFlush(claim);
    }

    @Test
    @DisplayName("findByIdAndOrder_MemberId: 본인 주문 반품만 조회 (타 회원 id 로는 빈 결과)")
    void findByIdAndOrderMemberId_scopesToOwner() {
        Order order = persistOrder(2);
        Claim claim = persistClaim(order, 1);

        assertThat(claimRepository.findByIdAndOrder_MemberId(claim.getId(), member.getId())).isPresent();
        assertThat(claimRepository.findByIdAndOrder_MemberId(claim.getId(), member.getId() + 999_999)).isEmpty();
    }

    @Test
    @DisplayName("findByOrder_IdAndStatusNot: REJECTED 를 제외한 주문 반품만 (잔여 수량 회계용)")
    void findByOrderIdAndStatusNot_excludesRejected() {
        Order order = persistOrder(3);
        Claim active = persistClaim(order, 1); // REQUESTED
        Claim rejected = persistClaim(order, 1);
        rejected.reject("반려", Instant.parse("2026-06-12T00:00:00Z"));
        claimRepository.saveAndFlush(rejected);

        List<Claim> result = claimRepository.findByOrder_IdAndStatusNot(order.getId(), ClaimStatus.REJECTED);

        assertThat(result).extracting(Claim::getId).contains(active.getId()).doesNotContain(rejected.getId());
    }

    @Test
    @DisplayName("findByStatusAndApprovedAtBefore: cutoff 이전 승인분만 (스케줄러 회수 대상)")
    void findByStatusAndApprovedAtBefore_filtersByTime() {
        Order order = persistOrder(2);
        Claim oldApproved = Claim.request(order, "변심");
        oldApproved.addItem(ClaimItem.of(order.getItems().get(0), 1));
        oldApproved.approve(Instant.parse("2020-01-01T00:00:00Z"));
        oldApproved = claimRepository.saveAndFlush(oldApproved);

        Order order2 = persistOrder(2);
        Claim recentApproved = Claim.request(order2, "변심");
        recentApproved.addItem(ClaimItem.of(order2.getItems().get(0), 1));
        recentApproved.approve(Instant.parse("2030-01-01T00:00:00Z"));
        recentApproved = claimRepository.saveAndFlush(recentApproved);

        List<Claim> result = claimRepository.findByStatusAndApprovedAtBefore(
                ClaimStatus.APPROVED, Instant.parse("2021-01-01T00:00:00Z"), Limit.of(1000));

        // 공유 DB라 타 테스트 데이터가 섞일 수 있어 자기 id 기준으로만 단언한다.
        assertThat(result).extracting(Claim::getId)
                .contains(oldApproved.getId())
                .doesNotContain(recentApproved.getId());
    }
}
