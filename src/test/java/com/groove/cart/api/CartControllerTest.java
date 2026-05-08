package com.groove.cart.api;

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
import com.groove.cart.domain.CartRepository;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/cart (장바구니 CRUD)")
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private JwtProvider jwtProvider;

    private String userBearer;
    private Long memberId;
    private Long sellingAlbumId;
    private Long hiddenAlbumId;
    private Long soldOutAlbumId;

    @BeforeEach
    void setUp() {
        // FK 의존 순서: cart_item → cart → album → artist/genre/label, member
        cartRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member member = memberRepository.saveAndFlush(
                Member.register("user@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "User", "01000000000"));
        memberId = member.getId();

        Long artistId = artistRepository.saveAndFlush(Artist.create("The Beatles", null)).getId();
        Long genreId = genreRepository.saveAndFlush(Genre.create("Rock")).getId();
        Long labelId = labelRepository.saveAndFlush(Label.create("Apple Records")).getId();

        Artist artist = artistRepository.findById(artistId).orElseThrow();
        Genre genre = genreRepository.findById(genreId).orElseThrow();
        Label label = labelRepository.findById(labelId).orElseThrow();

        sellingAlbumId = albumRepository.saveAndFlush(
                Album.create("Abbey Road", artist, genre, label,
                        (short) 1969, AlbumFormat.LP_12, 35000L, 10,
                        AlbumStatus.SELLING, false, null, null)).getId();
        hiddenAlbumId = albumRepository.saveAndFlush(
                Album.create("Hidden", artist, genre, label,
                        (short) 1970, AlbumFormat.LP_12, 30000L, 10,
                        AlbumStatus.HIDDEN, false, null, null)).getId();
        soldOutAlbumId = albumRepository.saveAndFlush(
                Album.create("Sold Out", artist, genre, label,
                        (short) 1971, AlbumFormat.LP_12, 25000L, 0,
                        AlbumStatus.SOLD_OUT, false, null, null)).getId();

        userBearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);
    }

    @Test
    @DisplayName("GET /api/v1/cart → 200, cart 미존재 시에도 빈 응답 (자동 영속화 안 함)")
    void getEmpty_returns200_doesNotPersist() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.totalItemCount").value(0));

        assertThat(cartRepository.findByMemberId(memberId)).isEmpty();
    }

    @Test
    @DisplayName("POST /api/v1/cart/items → 201, 처음이면 cart 자동 생성 + 항목 추가")
    void addItem_lazyCreatesCart() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemBody(sellingAlbumId, 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cartId").exists())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].albumId").value(sellingAlbumId))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(70000))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.totalAmount").value(70000))
                .andExpect(jsonPath("$.totalItemCount").value(2));

        assertThat(cartRepository.findByMemberId(memberId)).isPresent();
    }

    @Test
    @DisplayName("POST /api/v1/cart/items → 동일 album 재추가 시 quantity 누적, 행은 그대로 1")
    void addItem_accumulates() throws Exception {
        addSellingItem(2);
        addSellingItem(3);

        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.totalItemCount").value(5));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items HIDDEN album → 422 ALBUM_NOT_PURCHASABLE")
    void addItem_hiddenRejected() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemBody(hiddenAlbumId, 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_PURCHASABLE"));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items SOLD_OUT album → 422 ALBUM_NOT_PURCHASABLE")
    void addItem_soldOutRejected() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemBody(soldOutAlbumId, 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_PURCHASABLE"));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items 미존재 albumId → 404 ALBUM_NOT_FOUND")
    void addItem_unknownAlbum_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemBody(99_999L, 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items quantity 0 → 400 (Bean Validation @Min)")
    void addItem_zeroQuantity_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemBody(sellingAlbumId, 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/cart/items quantity 100(=MAX+1) → 400")
    void addItem_overMaxQuantity_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemBody(sellingAlbumId, 100))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/v1/cart/items/{itemId} → 200, quantity 교체")
    void changeItemQuantity_returns200() throws Exception {
        Long itemId = createItemAndExtractId(sellingAlbumId, 2);

        mockMvc.perform(patch("/api/v1/cart/items/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(7));
    }

    @Test
    @DisplayName("PATCH /api/v1/cart/items/{itemId} 본인 cart 외 itemId → 404 CART_ITEM_NOT_FOUND")
    void changeItemQuantity_alienItem_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/cart/items/{itemId}", 99_999L)
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/v1/cart/items/{itemId} → 204, 항목 제거")
    void removeItem_returns204() throws Exception {
        Long itemId = createItemAndExtractId(sellingAlbumId, 2);

        mockMvc.perform(delete("/api/v1/cart/items/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("DELETE /api/v1/cart → 204, 모든 항목 비움")
    void clear_returns204() throws Exception {
        addSellingItem(2);

        mockMvc.perform(delete("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/cart 인증 없음 → 401")
    void get_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
    }

    private Map<String, Object> itemBody(Long albumId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("albumId", albumId);
        body.put("quantity", quantity);
        return body;
    }

    private void addSellingItem(int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemBody(sellingAlbumId, quantity))))
                .andExpect(status().isCreated());
    }

    private Long createItemAndExtractId(Long albumId, int quantity) throws Exception {
        String body = mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemBody(albumId, quantity))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> response = objectMapper.readValue(body, Map.class);
        Object items = response.get("items");
        Map<?, ?> firstItem = (Map<?, ?>) ((java.util.List<?>) items).get(0);
        return Long.valueOf(firstItem.get("itemId").toString());
    }
}
