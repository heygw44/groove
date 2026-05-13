package com.groove.review.api;

import com.groove.auth.security.JwtProvider;
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
import com.groove.member.domain.MemberRole;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.review.domain.Review;
import com.groove.review.domain.ReviewRepository;
import com.groove.support.OrderFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 리뷰 작성/조회/삭제 + 카탈로그 평점 연동 통합 테스트 (#59).
 *
 * <p>Testcontainers MySQL 위에서 부팅된 MockMvc 로 실 필터·서비스·DB·Flyway(V13) 를 모두 거친다.
 * DoD: 모든 API 정상 동작 / 배송 완료 전 422 / 중복 409 / 타인 리뷰 삭제 차단 403 / 앨범 조회 응답에 평균 평점·리뷰 수 반영.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("리뷰 API 통합 — 작성/조회/삭제 + 카탈로그 평점 연동 (#59)")
class ReviewApiIntegrationTest {

    private static final AtomicInteger ORDER_SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
    private JwtProvider jwtProvider;

    private Long ownerId;
    private Long otherMemberId;
    private Long albumId;
    private String ownerBearer;
    private String otherBearer;

    @BeforeEach
    void setUp() {
        // FK 의존 순서: review → orders/album/member, orders → album, album → artist/genre/label
        reviewRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        ownerId = memberRepository.saveAndFlush(
                Member.register("owner@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "김민수", "01000000001")).getId();
        otherMemberId = memberRepository.saveAndFlush(
                Member.register("other@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "이영희", "01000000002")).getId();

        Long artistId = artistRepository.saveAndFlush(Artist.create("The Beatles", "desc")).getId();
        Long genreId = genreRepository.saveAndFlush(Genre.create("Rock")).getId();
        Long labelId = labelRepository.saveAndFlush(Label.create("Apple Records")).getId();
        Artist artist = artistRepository.findById(artistId).orElseThrow();
        Genre genre = genreRepository.findById(genreId).orElseThrow();
        Label label = labelRepository.findById(labelId).orElseThrow();
        albumId = albumRepository.saveAndFlush(
                Album.create("Abbey Road", artist, genre, label,
                        (short) 1969, AlbumFormat.LP_12, 35000L, 100,
                        AlbumStatus.SELLING, false, "https://img", "Mastered from the original tapes")).getId();

        ownerBearer = "Bearer " + jwtProvider.issueAccessToken(ownerId, MemberRole.USER);
        otherBearer = "Bearer " + jwtProvider.issueAccessToken(otherMemberId, MemberRole.USER);
    }

    private String nextOrderNumber() {
        return String.format("ORD-20260512-A%05d", ORDER_SEQ.incrementAndGet());
    }

    /** PENDING 에서 시작해 합법 전이만으로 {@code status} 까지 끌어올린 회원 주문을 영속화한다 (album 1점). */
    private Order persistMemberOrder(Long memberId, OrderStatus status) {
        Album album = albumRepository.findById(albumId).orElseThrow();
        Order order = Order.placeForMember(nextOrderNumber(), memberId, OrderFixtures.sampleShippingInfo());
        order.addItem(OrderItem.create(album, 1));
        for (OrderStatus next : pathTo(status)) {
            order.changeStatus(next, null);
        }
        return orderRepository.saveAndFlush(order);
    }

    private static List<OrderStatus> pathTo(OrderStatus target) {
        return switch (target) {
            case PENDING -> List.of();
            case PAID -> List.of(OrderStatus.PAID);
            case PREPARING -> List.of(OrderStatus.PAID, OrderStatus.PREPARING);
            case SHIPPED -> List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);
            case DELIVERED -> List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
            case COMPLETED -> List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.COMPLETED);
            default -> throw new IllegalArgumentException(target.name());
        };
    }

    private String createBody(String orderNumber, Long albumId, int rating, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNumber", orderNumber);
        body.put("albumId", albumId);
        body.put("rating", rating);
        body.put("content", content);
        return objectMapper.writeValueAsString(body);
    }

    private Long persistReview(Long memberId, OrderStatus orderStatus, int rating) {
        Order order = persistMemberOrder(memberId, orderStatus);
        Member member = memberRepository.findById(memberId).orElseThrow();
        Album album = albumRepository.findById(albumId).orElseThrow();
        return reviewRepository.saveAndFlush(Review.write(member, album, order, rating, "내용 " + rating)).getId();
    }

    // ---------- POST /reviews ----------

    @Test
    @DisplayName("DELIVERED 회원 주문 → 리뷰 작성 201 + 마스킹된 작성자명")
    void create_returns201() throws Exception {
        Order order = persistMemberOrder(ownerId, OrderStatus.DELIVERED);

        mockMvc.perform(post("/api/v1/reviews")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(order.getOrderNumber(), albumId, 5, "음질 정말 좋네요")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").isNumber())
                .andExpect(jsonPath("$.memberName").value("김**"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.content").value("음질 정말 좋네요"))
                .andExpect(jsonPath("$.createdAt").exists());

        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("COMPLETED 회원 주문 → 리뷰 작성 201")
    void create_completedOrder_returns201() throws Exception {
        Order order = persistMemberOrder(ownerId, OrderStatus.COMPLETED);

        mockMvc.perform(post("/api/v1/reviews")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(order.getOrderNumber(), albumId, 4, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    @DisplayName("같은 (주문, 앨범) 두 번째 작성 → 409 REVIEW_DUPLICATED")
    void create_duplicate_returns409() throws Exception {
        Order order = persistMemberOrder(ownerId, OrderStatus.DELIVERED);
        String body = createBody(order.getOrderNumber(), albumId, 5, "first");

        mockMvc.perform(post("/api/v1/reviews").header(HttpHeaders.AUTHORIZATION, ownerBearer)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/reviews").header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_DUPLICATED"));
    }

    @Test
    @DisplayName("배송 완료 전(SHIPPED) 주문 → 422 REVIEW_ORDER_NOT_DELIVERED")
    void create_notDelivered_returns422() throws Exception {
        Order order = persistMemberOrder(ownerId, OrderStatus.SHIPPED);

        mockMvc.perform(post("/api/v1/reviews")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(order.getOrderNumber(), albumId, 5, "이르다")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REVIEW_ORDER_NOT_DELIVERED"));

        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    @DisplayName("타인의 주문에 리뷰 작성 시도 → 403 REVIEW_NOT_OWNED")
    void create_notOwnersOrder_returns403() throws Exception {
        Order order = persistMemberOrder(otherMemberId, OrderStatus.DELIVERED);

        mockMvc.perform(post("/api/v1/reviews")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(order.getOrderNumber(), albumId, 5, "남의 주문")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_OWNED"));
    }

    @Test
    @DisplayName("주문에 없는 앨범으로 리뷰 작성 → 422 REVIEW_ALBUM_NOT_IN_ORDER")
    void create_albumNotInOrder_returns422() throws Exception {
        Order order = persistMemberOrder(ownerId, OrderStatus.DELIVERED);
        Album otherAlbum = albumRepository.saveAndFlush(Album.create("Let It Be",
                artistRepository.findAll().get(0), genreRepository.findAll().get(0), null,
                (short) 1970, AlbumFormat.LP_12, 30000L, 10, AlbumStatus.SELLING, false, null, null));

        mockMvc.perform(post("/api/v1/reviews")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(order.getOrderNumber(), otherAlbum.getId(), 5, "안 산 앨범")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REVIEW_ALBUM_NOT_IN_ORDER"));
    }

    @Test
    @DisplayName("존재하지 않는 주문 번호 → 404 ORDER_NOT_FOUND")
    void create_unknownOrder_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("ORD-NOPE", albumId, 5, "x")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("rating 범위 밖(6) → 400 HTTP_400")
    void create_invalidRating_returns400() throws Exception {
        Order order = persistMemberOrder(ownerId, OrderStatus.DELIVERED);

        mockMvc.perform(post("/api/v1/reviews")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(order.getOrderNumber(), albumId, 6, "범위밖")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HTTP_400"));
    }

    @Test
    @DisplayName("인증 없이 리뷰 작성 → 401")
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("ORD-X", albumId, 5, "x")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- GET /albums/{id}/reviews ----------

    @Test
    @DisplayName("상품 리뷰 목록 → 200, 작성자명 마스킹, createdAt desc 정렬")
    void list_returnsMaskedAndSorted() throws Exception {
        persistReview(ownerId, OrderStatus.DELIVERED, 5);
        persistReview(otherMemberId, OrderStatus.DELIVERED, 3);

        mockMvc.perform(get("/api/v1/albums/{id}/reviews", albumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].memberName").value("이**"))
                .andExpect(jsonPath("$.content[0].rating").value(3))
                .andExpect(jsonPath("$.content[1].memberName").value("김**"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("리뷰 목록 정렬 키 화이트리스트 밖(rating) → 400")
    void list_disallowedSort_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/albums/{id}/reviews", albumId).param("sort", "rating,desc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("리뷰가 없는 앨범 목록 → 200 빈 페이지")
    void list_noReviews_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/albums/{id}/reviews", albumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------- DELETE /reviews/{id} ----------

    @Test
    @DisplayName("본인 리뷰 삭제 → 204, 이후 목록에서 사라짐")
    void delete_ownReview_returns204() throws Exception {
        Long reviewId = persistReview(ownerId, OrderStatus.DELIVERED, 5);

        mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId).header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isNoContent());

        assertThat(reviewRepository.existsById(reviewId)).isFalse();
    }

    @Test
    @DisplayName("타인 리뷰 삭제 시도 → 403 REVIEW_NOT_OWNED, 리뷰 보존")
    void delete_othersReview_returns403() throws Exception {
        Long reviewId = persistReview(ownerId, OrderStatus.DELIVERED, 5);

        mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId).header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_OWNED"));

        assertThat(reviewRepository.existsById(reviewId)).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 리뷰 삭제 → 404 REVIEW_NOT_FOUND")
    void delete_missing_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/reviews/{id}", 999999L).header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    // ---------- 카탈로그 평점 연동 ----------

    @Test
    @DisplayName("리뷰 작성 후 GET /albums/{id} 응답에 averageRating(소수1자리)·reviewCount 반영")
    void albumDetail_reflectsRating() throws Exception {
        persistReview(ownerId, OrderStatus.DELIVERED, 5);
        persistReview(otherMemberId, OrderStatus.DELIVERED, 4);

        mockMvc.perform(get("/api/v1/albums/{id}", albumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(2));
    }

    @Test
    @DisplayName("리뷰 작성 후 GET /albums 목록 응답에도 평점·리뷰 수 반영, 리뷰 없는 앨범은 null/0")
    void albumList_reflectsRating() throws Exception {
        persistReview(ownerId, OrderStatus.DELIVERED, 5);
        persistReview(otherMemberId, OrderStatus.DELIVERED, 2);
        // 리뷰 없는 두 번째 앨범
        albumRepository.saveAndFlush(Album.create("Let It Be",
                artistRepository.findAll().get(0), genreRepository.findAll().get(0), null,
                (short) 1970, AlbumFormat.LP_12, 30000L, 10, AlbumStatus.SELLING, false, null, null));

        mockMvc.perform(get("/api/v1/albums").param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(albumId))
                .andExpect(jsonPath("$.content[0].averageRating").value(3.5))
                .andExpect(jsonPath("$.content[0].reviewCount").value(2))
                .andExpect(jsonPath("$.content[1].averageRating").doesNotExist())
                .andExpect(jsonPath("$.content[1].reviewCount").value(0));
    }

    @Test
    @DisplayName("평균 평점 반올림 — (5+5+4)/3=4.666… → 4.7")
    void albumDetail_roundsAverage() throws Exception {
        persistReview(ownerId, OrderStatus.DELIVERED, 5);
        persistReview(ownerId, OrderStatus.DELIVERED, 5);
        persistReview(otherMemberId, OrderStatus.DELIVERED, 4);

        mockMvc.perform(get("/api/v1/albums/{id}", albumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.7));
    }
}
