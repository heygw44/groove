package com.groove.order;

import com.groove.auth.security.JwtProvider;
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
import com.groove.member.domain.MemberRole;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 장바구니 → 주문 통합 E2E (#45).
 *
 * <p>cart / order 단일 도메인 컨트롤러 테스트({@code CartControllerTest},
 * {@code OrderControllerTest}, {@code MemberOrderControllerTest})와 중복되지 않도록,
 * 본 테스트는 두 도메인을 가로지르는 사용자 흐름과 회귀 위험이 큰 결합 지점만 다룬다:
 * <ul>
 *   <li>회원 cart → order 생성 → 단건 + 목록 조회의 결합 동작</li>
 *   <li>게스트 직접 주문 생성 → guest-lookup 본인 조회</li>
 *   <li>PENDING 주문 취소 → album 재고 복원 (서비스 단위로 검증되지만 실 DB 끝점에서 회귀 방지)</li>
 *   <li>타인 주문 cancel 차단 시 재고가 복원되지 않는 invariant</li>
 *   <li>한 요청에 SELLING+HIDDEN 라인 혼재 시 트랜잭션 롤백으로 SELLING 재고 미차감</li>
 *   <li>SOLD_OUT album 의 cart 추가 거부</li>
 * </ul>
 *
 * <p>Testcontainers MySQL 위에서 동작하며 부팅된 MockMvc 로 실 필터·서비스·DB 를 모두 거친다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("장바구니 → 주문 E2E (#45)")
class CartOrderE2EIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CartRepository cartRepository;

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

    private String memberABearer;
    private String memberBBearer;
    private Long memberAId;
    private Long sellingAlbumId;
    private Long anotherSellingAlbumId;
    private Long hiddenAlbumId;
    private Long soldOutAlbumId;

    private static final long SELLING_PRICE = 35_000L;
    private static final long ANOTHER_PRICE = 30_000L;
    private static final int INITIAL_STOCK = 10;

    @BeforeEach
    void setUp() {
        // FK 의존 순서대로 부모 repository 를 비운다 — cart_item / order_item 자식 행은
        // DB FK ON DELETE CASCADE / JPA 설정에 의해 부모 삭제와 함께 정리된다.
        cartRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member memberA = memberRepository.saveAndFlush(
                Member.register("a@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "A", "01000000001"));
        Member memberB = memberRepository.saveAndFlush(
                Member.register("b@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "B", "01000000002"));
        memberAId = memberA.getId();

        Long artistId = artistRepository.saveAndFlush(Artist.create("The Beatles", null)).getId();
        Long genreId = genreRepository.saveAndFlush(Genre.create("Rock")).getId();
        Long labelId = labelRepository.saveAndFlush(Label.create("Apple Records")).getId();

        Artist artist = artistRepository.findById(artistId).orElseThrow();
        Genre genre = genreRepository.findById(genreId).orElseThrow();
        Label label = labelRepository.findById(labelId).orElseThrow();

        sellingAlbumId = albumRepository.saveAndFlush(
                Album.create("Abbey Road", artist, genre, label,
                        (short) 1969, AlbumFormat.LP_12, SELLING_PRICE, INITIAL_STOCK,
                        AlbumStatus.SELLING, false, null, null)).getId();
        anotherSellingAlbumId = albumRepository.saveAndFlush(
                Album.create("Let It Be", artist, genre, label,
                        (short) 1970, AlbumFormat.LP_12, ANOTHER_PRICE, INITIAL_STOCK,
                        AlbumStatus.SELLING, false, null, null)).getId();
        hiddenAlbumId = albumRepository.saveAndFlush(
                Album.create("Hidden", artist, genre, label,
                        (short) 1970, AlbumFormat.LP_12, ANOTHER_PRICE, INITIAL_STOCK,
                        AlbumStatus.HIDDEN, false, null, null)).getId();
        soldOutAlbumId = albumRepository.saveAndFlush(
                Album.create("Sold Out", artist, genre, label,
                        (short) 1971, AlbumFormat.LP_12, 25_000L, 0,
                        AlbumStatus.SOLD_OUT, false, null, null)).getId();

        memberABearer = "Bearer " + jwtProvider.issueAccessToken(memberAId, MemberRole.USER);
        memberBBearer = "Bearer " + jwtProvider.issueAccessToken(memberB.getId(), MemberRole.USER);
    }

    @Test
    @DisplayName("회원: cart 에 담기 → 주문 생성 → 단건/목록 조회 (cart 항목과 일치)")
    void memberFlow_cartToOrder_listsAndFetches() throws Exception {
        // 1) cart 에 두 album 담기 — cart 추가는 album 재고를 차감하지 않음 (예약 없음, W6 정책)
        addToCart(memberABearer, sellingAlbumId, 2);
        addToCart(memberABearer, anotherSellingAlbumId, 3);

        assertThat(albumRepository.findById(sellingAlbumId).orElseThrow().getStock())
                .as("cart 추가는 재고를 차감하지 않는다")
                .isEqualTo(INITIAL_STOCK);

        // 2) 주문 생성 — cart 라인 그대로 OrderCreateRequest 로 전달
        long expectedTotal = SELLING_PRICE * 2 + ANOTHER_PRICE * 3;
        String orderNumber = placeMemberOrder(
                memberABearer,
                List.of(itemBody(sellingAlbumId, 2), itemBody(anotherSellingAlbumId, 3)));

        // 3) 주문 시점에 album 재고 차감 반영
        assertThat(albumRepository.findById(sellingAlbumId).orElseThrow().getStock()).isEqualTo(INITIAL_STOCK - 2);
        assertThat(albumRepository.findById(anotherSellingAlbumId).orElseThrow().getStock()).isEqualTo(INITIAL_STOCK - 3);

        // 4) 단건 조회 — items / totalAmount 일치
        mockMvc.perform(get("/api/v1/orders/" + orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, memberABearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.status").value(OrderStatus.PENDING.name()))
                .andExpect(jsonPath("$.totalAmount").value(expectedTotal))
                .andExpect(jsonPath("$.items.length()").value(2));

        // 5) 회원 목록에 1건 노출 — /members/me/orders
        mockMvc.perform(get("/api/v1/members/me/orders")
                        .header(HttpHeaders.AUTHORIZATION, memberABearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.content[0].status").value(OrderStatus.PENDING.name()))
                .andExpect(jsonPath("$.content[0].totalAmount").value(expectedTotal));

        // 6) 현재 정책: 주문 생성 후 cart 는 자동 비워지지 않는다 (W7+ 결제 흐름에서 결정).
        //    회귀 신호용 — 정책이 바뀌면 본 단언으로 즉시 드러난다.
        assertThat(cartRepository.findByMemberId(memberAId)).isPresent();
    }

    @Test
    @DisplayName("게스트: 직접 주문 생성 → guest-lookup 으로 본인 조회 (오답 email 은 404)")
    void guestFlow_placeAndLookup() throws Exception {
        String email = "guest@example.com";
        String orderNumber = placeGuestOrder(
                List.of(itemBody(sellingAlbumId, 1)),
                email, "01099998888");

        // 정상 lookup
        mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/guest-lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.totalAmount").value(SELLING_PRICE));

        // email 불일치 — 존재 노출 회피로 404 ORDER_NOT_FOUND
        mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/guest-lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "wrong@example.com"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("PENDING 주문 취소 → CANCELLED + album 재고 복원")
    void cancelPendingOrder_restoresStock() throws Exception {
        String orderNumber = placeMemberOrder(
                memberABearer, List.of(itemBody(sellingAlbumId, 4)));

        assertThat(albumRepository.findById(sellingAlbumId).orElseThrow().getStock())
                .isEqualTo(INITIAL_STOCK - 4);

        mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, memberABearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "단순 변심"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.CANCELLED.name()));

        assertThat(albumRepository.findById(sellingAlbumId).orElseThrow().getStock())
                .as("취소 시 차감했던 재고가 그대로 복원된다")
                .isEqualTo(INITIAL_STOCK);
    }

    @Test
    @DisplayName("타 회원이 cancel 시도 → 404 + 재고 복원 없음 (방어적 invariant)")
    void cancelByOtherMember_blockedAndStockUnchanged() throws Exception {
        String orderNumber = placeMemberOrder(
                memberABearer, List.of(itemBody(sellingAlbumId, 3)));
        int afterPlace = albumRepository.findById(sellingAlbumId).orElseThrow().getStock();
        assertThat(afterPlace).isEqualTo(INITIAL_STOCK - 3);

        mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, memberBBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(albumRepository.findById(sellingAlbumId).orElseThrow().getStock())
                .as("권한 차단 시 재고는 절대 복원되지 않는다")
                .isEqualTo(afterPlace);
    }

    @Test
    @DisplayName("타 회원 주문 GET → 404 (존재 노출 회피)")
    void getByOtherMember_returns404() throws Exception {
        String orderNumber = placeMemberOrder(
                memberABearer, List.of(itemBody(sellingAlbumId, 1)));

        mockMvc.perform(get("/api/v1/orders/" + orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, memberBBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("한 요청에 SELLING+HIDDEN 혼재 → 422 + SELLING 재고는 트랜잭션 롤백으로 미차감")
    void orderWithHiddenAlbum_rollsBackSellingStock() throws Exception {
        Map<String, Object> body = Map.of(
                "items", List.of(
                        itemBody(sellingAlbumId, 2),
                        itemBody(hiddenAlbumId, 1)));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, memberABearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_PURCHASABLE"));

        assertThat(albumRepository.findById(sellingAlbumId).orElseThrow().getStock())
                .as("HIDDEN 라인에서 RuntimeException → 트랜잭션 롤백으로 SELLING 재고는 그대로")
                .isEqualTo(INITIAL_STOCK);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("SOLD_OUT album 을 cart 에 추가 → 422 ALBUM_NOT_PURCHASABLE")
    void addSoldOutToCart_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, memberABearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemBody(soldOutAlbumId, 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_PURCHASABLE"));

        assertThat(cartRepository.findByMemberId(memberAId)).isEmpty();
    }

    private void addToCart(String bearer, Long albumId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemBody(albumId, quantity))))
                .andExpect(status().isCreated());
    }

    private String placeMemberOrder(String bearer, List<Map<String, Object>> items) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractOrderNumber(result);
    }

    private String placeGuestOrder(List<Map<String, Object>> items, String email, String phone) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("guest", Map.of("email", email, "phone", phone));

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractOrderNumber(result);
    }

    private String extractOrderNumber(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return requireField(json, "orderNumber").asText();
    }

    private static Map<String, Object> itemBody(Long albumId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("albumId", albumId);
        body.put("quantity", quantity);
        return body;
    }

    private static JsonNode requireField(JsonNode json, String field) {
        return Objects.requireNonNull(json.get(field), () -> "응답 JSON 에 '" + field + "' 필드가 없음: " + json);
    }
}
