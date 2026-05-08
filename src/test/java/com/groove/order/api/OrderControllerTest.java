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
import com.groove.order.domain.Order;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/orders 주문 생성 API")
class OrderControllerTest {

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
    private Long memberId;
    private Long sellingAlbumId;
    private Long anotherSellingAlbumId;
    private Long hiddenAlbumId;
    private Long lowStockAlbumId;

    @BeforeEach
    void setUp() {
        // FK 의존 순서: order_item → orders → album → artist/genre/label, member
        orderRepository.deleteAllInBatch();
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
                        (short) 1969, AlbumFormat.LP_12, 35000L, 100,
                        AlbumStatus.SELLING, false, null, null)).getId();
        anotherSellingAlbumId = albumRepository.saveAndFlush(
                Album.create("Let It Be", artist, genre, label,
                        (short) 1970, AlbumFormat.LP_12, 30000L, 100,
                        AlbumStatus.SELLING, false, null, null)).getId();
        hiddenAlbumId = albumRepository.saveAndFlush(
                Album.create("Hidden", artist, genre, label,
                        (short) 1970, AlbumFormat.LP_12, 30000L, 100,
                        AlbumStatus.HIDDEN, false, null, null)).getId();
        lowStockAlbumId = albumRepository.saveAndFlush(
                Album.create("Low Stock", artist, genre, label,
                        (short) 1971, AlbumFormat.LP_12, 25000L, 2,
                        AlbumStatus.SELLING, false, null, null)).getId();

        userBearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);
    }

    @Test
    @DisplayName("회원 주문 → 201, items·totalAmount·orderNumber 검증, 재고 차감 반영")
    void create_member_returns201() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(
                itemBody(sellingAlbumId, 2),
                itemBody(anotherSellingAlbumId, 3)));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/orders/ORD-")))
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.matchesRegex("^ORD-\\d{8}-[A-Z0-9]{6}$")))
                .andExpect(jsonPath("$.status").value(OrderStatus.PENDING.name()))
                .andExpect(jsonPath("$.totalAmount").value(35000 * 2 + 30000 * 3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].albumTitle").value("Abbey Road"))
                .andExpect(jsonPath("$.items[0].subtotal").value(70000));

        assertThat(albumRepository.findById(sellingAlbumId).orElseThrow().getStock()).isEqualTo(98);
        assertThat(albumRepository.findById(anotherSellingAlbumId).orElseThrow().getStock()).isEqualTo(97);
    }

    @Test
    @DisplayName("게스트 주문 → 201, memberId 미부착, guestEmail 보존")
    void create_guest_returns201() throws Exception {
        Map<String, Object> guest = new LinkedHashMap<>();
        guest.put("email", "guest@example.com");
        guest.put("phone", "01098765432");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(itemBody(sellingAlbumId, 1)));
        body.put("guest", guest);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(35000));

        Order saved = orderRepository.findAll().get(0);
        assertThat(saved.isGuestOrder()).isTrue();
        assertThat(saved.getGuestEmail()).isEqualTo("guest@example.com");
        assertThat(saved.getGuestPhone()).isEqualTo("01098765432");
    }

    @Test
    @DisplayName("재고 부족 → 409 ORDER_INSUFFICIENT_STOCK, 재고 미차감")
    void create_insufficientStock_returns409() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(itemBody(lowStockAlbumId, 5)));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INSUFFICIENT_STOCK"));

        assertThat(albumRepository.findById(lowStockAlbumId).orElseThrow().getStock()).isEqualTo(2);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("HIDDEN 앨범 → 422 ALBUM_NOT_PURCHASABLE")
    void create_hiddenAlbum_returns422() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(itemBody(hiddenAlbumId, 1)));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_PURCHASABLE"));
    }

    @Test
    @DisplayName("미존재 albumId → 404 ALBUM_NOT_FOUND")
    void create_unknownAlbum_returns404() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(itemBody(99_999L, 1)));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
    }

    @Test
    @DisplayName("게스트 정보 누락(헤더 없고 guest 도 null) → 422 ORDER_INVALID_OWNERSHIP")
    void create_anonymousWithoutGuest_returns422() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(itemBody(sellingAlbumId, 1)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_OWNERSHIP"));
    }

    @Test
    @DisplayName("게스트 phone 형식 위반 → 400 VALIDATION_FAILED")
    void create_guestInvalidPhone_returns400() throws Exception {
        Map<String, Object> guest = new LinkedHashMap<>();
        guest.put("email", "guest@example.com");
        guest.put("phone", "abc");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(itemBody(sellingAlbumId, 1)));
        body.put("guest", guest);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("items 배열 비어있음 → 400")
    void create_emptyItems_returns400() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of());

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("quantity 0 → 400")
    void create_zeroQuantity_returns400() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(itemBody(sellingAlbumId, 0)));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    private Map<String, Object> itemBody(Long albumId, int quantity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("albumId", albumId);
        item.put("quantity", quantity);
        return item;
    }
}
