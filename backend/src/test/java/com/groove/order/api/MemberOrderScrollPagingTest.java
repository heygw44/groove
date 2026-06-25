package com.groove.order.api;

import com.groove.auth.domain.RefreshTokenRepository;
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
import com.groove.support.MemberFixtures;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/members/me/orders/scroll (keyset 커서 페이징) MockMvc 통합 테스트.
 * 회원 스코프·인증 경계(타 회원 격리·401)와 커서 walk 의 누락·중복 없는 커버를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("GET /api/v1/members/me/orders/scroll (keyset 커서 페이징)")
class MemberOrderScrollPagingTest {

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

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String userBearer;
    private Long albumA;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member member = memberRepository.saveAndFlush(
                MemberFixtures.register("user@example.com", "$2a$10$dummy...", "User", "01000000000"));

        Artist artist = artistRepository.saveAndFlush(Artist.create("Artist", null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock"));
        Label label = labelRepository.saveAndFlush(Label.create("Label"));
        albumA = albumRepository.saveAndFlush(
                Album.create("Album A", artist, genre, label,
                        (short) 2020, AlbumFormat.LP_12, 30000L, 1000,
                        AlbumStatus.SELLING, false, null, null)).getId();

        userBearer = "Bearer " + jwtProvider.issueAccessToken(member.getId(), MemberRole.USER);
    }

    @Test
    @DisplayName("주문 0건 → 200, content 비어 있음 + hasNext=false + nextCursor 없음")
    void empty_returnsEmptyWindow() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/orders/scroll")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("주문 5건을 size=2 로 walk → 누락·중복 없이 본인 주문 전체 커버")
    void walk_coversAllOwnOrdersExactlyOnce() throws Exception {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            expected.add(placeOrder(userBearer));
        }

        List<String> collected = walkAll(userBearer, 2);

        // 기본 정렬 createdAt DESC + id DESC tiebreaker → 생성 역순. 순서까지 단언해 정렬 결정성 검증.
        List<String> expectedDesc = new ArrayList<>(expected);
        Collections.reverse(expectedDesc);
        assertThat(collected).containsExactlyElementsOf(expectedDesc);
    }

    @Test
    @DisplayName("인증 누락 → 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/orders/scroll"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("타 회원 주문은 본인 스크롤에 노출되지 않음")
    void excludesOtherMembers() throws Exception {
        Member other = memberRepository.saveAndFlush(
                MemberFixtures.register("other@example.com", "$2a$10$dummy...", "Other", "01000000001"));
        String otherBearer = "Bearer " + jwtProvider.issueAccessToken(other.getId(), MemberRole.USER);
        placeOrder(otherBearer);

        mockMvc.perform(get("/api/v1/members/me/orders/scroll")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("status 필터 — PENDING 은 본인 주문, 해당 없는 DELIVERED 는 빈 결과(필터 실제 적용 검증)")
    void filterByStatus() throws Exception {
        placeOrder(userBearer); // 생성 직후 상태는 PENDING

        // 매칭 상태 → 1건
        mockMvc.perform(get("/api/v1/members/me/orders/scroll")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        // 비매칭 상태 → 0건. 필터가 무시되면(전체 반환) 이 단언이 깨져 회귀를 잡는다(음성 케이스).
        mockMvc.perform(get("/api/v1/members/me/orders/scroll")
                        .param("status", "DELIVERED")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("잘못된 커서 → 400 VALID_001")
    void invalidCursor_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/orders/scroll")
                        .param("cursor", "not-a-valid-cursor!!!")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    private List<String> walkAll(String bearer, int size) throws Exception {
        List<String> collected = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 100; guard++) {
            JsonNode page = scroll(bearer, cursor, size);
            for (JsonNode item : page.get("content")) {
                collected.add(item.get("orderNumber").asString());
            }
            if (!page.get("hasNext").asBoolean()) {
                return collected;
            }
            cursor = page.get("nextCursor").asString();
        }
        throw new AssertionError("스크롤이 종료되지 않음 — 무한 루프 의심");
    }

    private JsonNode scroll(String bearer, String cursor, int size) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/members/me/orders/scroll")
                .param("size", String.valueOf(size))
                .header(HttpHeaders.AUTHORIZATION, bearer);
        if (cursor != null) {
            request = request.param("cursor", cursor);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** 주문 1건 생성 후 주문번호 반환. */
    private String placeOrder(String bearer) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("albumId", albumA);
        item.put("quantity", 1);

        Map<String, Object> shipping = new LinkedHashMap<>();
        shipping.put("recipientName", "김철수");
        shipping.put("recipientPhone", "01012345678");
        shipping.put("address", "서울시 강남구 테헤란로 123");
        shipping.put("addressDetail", "456호");
        shipping.put("zipCode", "06234");
        shipping.put("safePackagingRequested", false);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("shipping", shipping);

        String response = mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("orderNumber").asString();
    }
}
