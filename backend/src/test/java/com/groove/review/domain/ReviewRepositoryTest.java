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

    // review 는 member/album/order FK 가 모두 선행 존재해야 한다. 각 테스트는 자기 앨범을 새로 만들어
    // findByAlbumId(albumId) 를 그 앨범으로 스코프한다 — album 단위 조회라 공유 Testcontainers 에서도 격리된다.
    private Member member;
    private Album album;
    private int orderSeq;

    @BeforeEach
    void setUp() {
        member = memberRepository.saveAndFlush(
                MemberFixtures.register("review-repo-test@example.com", "$2a$12$hash", "리뷰어", "01012345678"));

        // genre.name 등은 UNIQUE 라, 공유 Testcontainers 에 다른 @SpringBootTest 가 커밋한 'Rock' 등과 충돌하지 않도록
        // 테스트마다 유니크한 이름을 쓴다(자기 id 로 스코프하는 격리 규칙의 셋업 버전).
        String uniq = "-" + System.nanoTime();
        Artist artist = artistRepository.saveAndFlush(Artist.create("The Beatles" + uniq, "desc"));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock" + uniq));
        Label label = labelRepository.saveAndFlush(Label.create("Apple Records" + uniq));
        album = albumRepository.saveAndFlush(
                Album.create("Abbey Road", artist, genre, label,
                        (short) 1969, AlbumFormat.LP_12, 35000L, 100,
                        AlbumStatus.SELLING, false, "https://img", "원본 테이프 마스터링"));
    }

    // 앨범에 리뷰 1건을 심는다. uk_review_order_album(order_id, album_id) 때문에 리뷰마다 별도 주문이 필요하다.
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
        // @EntityGraph(member) 로 작성자가 함께 로드된다 — 마스킹용 이름 접근이 LAZY 추가 조회 없이 가능.
        assertThat(page.getContent()).allSatisfy(r -> assertThat(r.getMember().getName()).isNotBlank());
    }

    @Test
    @DisplayName("[#225] 리뷰 목록 복합 인덱스가 V22 에서 도입됨 — (album_id, created_at)")
    void albumReviewIndex_isAdded() {
        // V13 헤더의 [W10] 의도적 누락 인덱스를 V22 에서 도입했다(filesort Before→After 시연 완료).
        // 가드가 깨지면(인덱스 누락) 리뷰 목록 정렬 개선 회귀 신호다 — AlbumRepositoryTest.searchIndexes_areAdded 와 동일 의도.
        @SuppressWarnings("unchecked")
        List<String> indexNames = (List<String>) em.createNativeQuery(
                "SELECT INDEX_NAME FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' " +
                        "GROUP BY INDEX_NAME")
                .getResultList();

        assertThat(indexNames).contains("idx_review_album_created");
    }
}
