package com.groove.review.domain;

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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("ReviewRepository 통합 테스트 (Testcontainers MySQL)")
class ReviewRepositoryTest {

    @Autowired
    private ReviewRepository reviewRepository;
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
    @Autowired
    private EntityManager em;

    // 각 테스트는 자기 앨범을 새로 만들어 findByAlbumId 를 그 앨범으로 스코프한다.
    private Member member;
    private Album album;
    private int orderSeq;

    @BeforeEach
    void setUp() {
        member = memberRepository.saveAndFlush(
                MemberFixtures.register("review-repo-test@example.com", "$2a$12$hash", "리뷰어", "01012345678"));

        // genre.name 등이 UNIQUE 라 충돌하지 않도록 테스트마다 유니크한 이름을 쓴다.
        String uniq = "-" + System.nanoTime();
        Artist artist = artistRepository.saveAndFlush(Artist.create("The Beatles" + uniq, "desc"));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock" + uniq));
        Label label = labelRepository.saveAndFlush(Label.create("Apple Records" + uniq));
        album = albumRepository.saveAndFlush(
                Album.create("Abbey Road", artist, genre, label,
                        (short) 1969, AlbumFormat.LP_12, 35000L, 100,
                        AlbumStatus.SELLING, false, "https://img", "원본 테이프 마스터링"));
    }

    // 앨범에 리뷰 1건을 심는다. uk_review_order_album 때문에 리뷰마다 별도 주문이 필요하다.
    private Review persistReview(int rating) {
        Order order = Order.placeForMember(
                "ORD-IDX-" + (++orderSeq) + "-" + System.nanoTime(), member.getId(), OrderFixtures.sampleShippingInfo());
        order.addItem(OrderItem.create(album, 1));
        order = orderRepository.saveAndFlush(order);
        return reviewRepository.saveAndFlush(Review.write(member, album, order, rating, "내용 " + rating));
    }

    @Test
    @DisplayName("findByAlbumId(#225) — 앨범 리뷰를 created_at DESC 페이지로 반환 + 작성자(member) 페치")
    void findByAlbumId_returnsAlbumReviewsSortedByCreatedAtDesc_withMemberFetched() {
        Review r1 = persistReview(5);
        Review r2 = persistReview(4);
        Review r3 = persistReview(3);

        Page<Review> page = reviewRepository.findByAlbumId(
                album.getId(), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getContent())
                .extracting(Review::getId)
                .containsExactlyInAnyOrder(r1.getId(), r2.getId(), r3.getId());
        assertThat(page.getContent())
                .isSortedAccordingTo(Comparator.comparing(Review::getCreatedAt).reversed());
        // @EntityGraph(member) 로 작성자가 함께 로드돼 이름 접근이 LAZY 추가 조회 없이 가능.
        assertThat(page.getContent()).allSatisfy(r -> assertThat(r.getMember().getName()).isNotBlank());
    }

    @Test
    @DisplayName("[#225] 리뷰 목록 복합 인덱스가 V22 에서 도입됨 — (album_id, created_at)")
    void albumReviewIndex_isAdded() {
        // review 테이블에 (album_id, created_at) 복합 인덱스가 존재하는지 확인한다.
        @SuppressWarnings("unchecked")
        List<String> indexNames = (List<String>) em.createNativeQuery(
                "SELECT INDEX_NAME FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' " +
                        "GROUP BY INDEX_NAME")
                .getResultList();

        assertThat(indexNames).contains("idx_review_album_created");
    }
}
