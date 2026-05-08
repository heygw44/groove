package com.groove.order.api;

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
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/members/me/orders 회원 주문 목록 API")
class MemberOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private String userBearer;
    private Long albumA;
    private Long albumB;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member member = memberRepository.saveAndFlush(
                Member.register("user@example.com", "$2a$10$dummy...", "User", "01000000000"));

        Artist artist = artistRepository.saveAndFlush(Artist.create("Artist", null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock"));
        Label label = labelRepository.saveAndFlush(Label.create("Label"));

        albumA = albumRepository.saveAndFlush(
                Album.create("Album A", artist, genre, label,
                        (short) 2020, AlbumFormat.LP_12, 30000L, 100,
                        AlbumStatus.SELLING, false, null, null)).getId();
        albumB = albumRepository.saveAndFlush(
                Album.create("Album B", artist, genre, label,
                        (short) 2021, AlbumFormat.LP_12, 20000L, 100,
                        AlbumStatus.SELLING, false, null, null)).getId();

        userBearer = "Bearer " + jwtProvider.issueAccessToken(member.getId(), MemberRole.USER);
    }

    @Test
    @DisplayName("주문이 0건 → 200, content 비어 있음")
    void list_empty_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/orders")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("주문 2건 생성 후 목록 조회 → 200, itemCount/representativeAlbumTitle 포함")
    void list_withTwoOrders_returnsSummaries() throws Exception {
        placeOrder(List.of(item(albumA, 2), item(albumB, 1)));
        placeOrder(List.of(item(albumB, 3)));

        mockMvc.perform(get("/api/v1/members/me/orders")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].itemCount").exists())
                .andExpect(jsonPath("$.content[0].representativeAlbumTitle").exists());
    }

    @Test
    @DisplayName("status 필터 — PENDING 만 조회")
    void list_filterByStatus() throws Exception {
        placeOrder(List.of(item(albumA, 1)));

        mockMvc.perform(get("/api/v1/members/me/orders")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value(OrderStatus.PENDING.name()));
    }

    @Test
    @DisplayName("status 필터 — DELIVERED (해당 없음) → 빈 목록")
    void list_filterDelivered_empty() throws Exception {
        placeOrder(List.of(item(albumA, 1)));

        mockMvc.perform(get("/api/v1/members/me/orders")
                        .param("status", "DELIVERED")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("인증 누락 → 401")
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("타 회원 주문은 본인 목록에 노출되지 않음")
    void list_excludesOtherMembers() throws Exception {
        Member other = memberRepository.saveAndFlush(
                Member.register("other@example.com", "$2a$10$dummy...", "Other", "01000000001"));
        String otherBearer = "Bearer " + jwtProvider.issueAccessToken(other.getId(), MemberRole.USER);

        placeOrderWith(otherBearer, List.of(item(albumA, 1)));

        mockMvc.perform(get("/api/v1/members/me/orders")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("허용되지 않은 정렬 키 → 400 VALIDATION_FAILED")
    void list_invalidSort_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/orders")
                        .param("sort", "totalAmount,desc")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    private void placeOrder(List<Map<String, Object>> items) throws Exception {
        placeOrderWith(userBearer, items);
    }

    private void placeOrderWith(String bearer, List<Map<String, Object>> items) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private Map<String, Object> item(Long albumId, int quantity) {
        Map<String, Object> i = new LinkedHashMap<>();
        i.put("albumId", albumId);
        i.put("quantity", quantity);
        return i;
    }
}
