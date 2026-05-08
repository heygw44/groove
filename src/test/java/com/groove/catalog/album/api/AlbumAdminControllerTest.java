package com.groove.catalog.album.api;

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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/admin/albums (ADMIN CRUD + 재고 조정)")
class AlbumAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private String adminBearer;
    private String userBearer;

    private Long artistId;
    private Long genreId;
    private Long labelId;

    @BeforeEach
    void setUp() {
        // FK 의존 순서: album → artist/genre/label (album 먼저 비워야 부모 삭제 가능)
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();

        artistId = artistRepository.saveAndFlush(Artist.create("The Beatles", null)).getId();
        genreId = genreRepository.saveAndFlush(Genre.create("Rock")).getId();
        labelId = labelRepository.saveAndFlush(Label.create("Apple Records")).getId();

        adminBearer = "Bearer " + jwtProvider.issueAccessToken(1L, MemberRole.ADMIN);
        userBearer = "Bearer " + jwtProvider.issueAccessToken(2L, MemberRole.USER);
    }

    private Map<String, Object> validCreateBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Abbey Road");
        body.put("artistId", artistId);
        body.put("genreId", genreId);
        body.put("labelId", labelId);
        body.put("releaseYear", 1969);
        body.put("format", "LP_12");
        body.put("price", 35000);
        body.put("stock", 8);
        body.put("status", "SELLING");
        body.put("isLimited", false);
        body.put("coverImageUrl", null);
        body.put("description", null);
        return body;
    }

    @Test
    @DisplayName("POST → 201 + Location + DB 영속")
    void create_returns201() throws Exception {
        Map<String, Object> body = validCreateBody();

        mockMvc.perform(post("/api/v1/admin/albums")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Abbey Road"))
                .andExpect(jsonPath("$.artist.id").value(artistId))
                .andExpect(jsonPath("$.genre.id").value(genreId))
                .andExpect(jsonPath("$.label.id").value(labelId))
                .andExpect(jsonPath("$.stock").value(8))
                .andExpect(jsonPath("$.status").value("SELLING"));

        assertThat(albumRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST labelId 생략 → 201 + 응답에 label 정보 없음 (필드 null 또는 omit)")
    void create_withoutLabel_returns201() throws Exception {
        Map<String, Object> body = validCreateBody();
        body.remove("labelId");

        // label 은 nullable 필드. Jackson 의 null record 직렬화 동작 (null vs omit) 에 의존하지 않도록
        // 중첩 id/name 이 보이지 않음을 직접 확인한다 — 어느 쪽이든 클라이언트가 의미 있는 값을 못 받는 것이 계약.
        mockMvc.perform(post("/api/v1/admin/albums")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label.id").doesNotExist())
                .andExpect(jsonPath("$.label.name").doesNotExist());

        Long persistedId = albumRepository.findAll().stream()
                .findFirst()
                .orElseThrow()
                .getId();
        assertThat(albumRepository.findById(persistedId).orElseThrow().getLabel()).isNull();
    }

    @Test
    @DisplayName("POST 미존재 artistId → 404 ARTIST_NOT_FOUND")
    void create_unknownArtist_returns404() throws Exception {
        Map<String, Object> body = validCreateBody();
        body.put("artistId", 99_999);

        mockMvc.perform(post("/api/v1/admin/albums")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));

        assertThat(albumRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST 미존재 genreId → 404 GENRE_NOT_FOUND")
    void create_unknownGenre_returns404() throws Exception {
        Map<String, Object> body = validCreateBody();
        body.put("genreId", 99_999);

        mockMvc.perform(post("/api/v1/admin/albums")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENRE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST 미존재 labelId → 404 LABEL_NOT_FOUND")
    void create_unknownLabel_returns404() throws Exception {
        Map<String, Object> body = validCreateBody();
        body.put("labelId", 99_999);

        mockMvc.perform(post("/api/v1/admin/albums")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LABEL_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST 음수 price → 400")
    void create_negativePrice_returns400() throws Exception {
        Map<String, Object> body = validCreateBody();
        body.put("price", -1);

        mockMvc.perform(post("/api/v1/admin/albums")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST 음수 stock → 400")
    void create_negativeStock_returns400() throws Exception {
        Map<String, Object> body = validCreateBody();
        body.put("stock", -1);

        mockMvc.perform(post("/api/v1/admin/albums")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST 빈 title → 400")
    void create_blankTitle_returns400() throws Exception {
        Map<String, Object> body = validCreateBody();
        body.put("title", "  ");

        mockMvc.perform(post("/api/v1/admin/albums")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST 잘못된 enum 값 → 400")
    void create_invalidEnum_returns400() throws Exception {
        Map<String, Object> body = validCreateBody();
        body.put("format", "INVALID_FORMAT");

        mockMvc.perform(post("/api/v1/admin/albums")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST 미인증 → 401")
    void create_unauthenticated_returns401() throws Exception {
        Map<String, Object> body = validCreateBody();

        mockMvc.perform(post("/api/v1/admin/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST USER 권한 → 403")
    void create_userRole_returns403() throws Exception {
        Map<String, Object> body = validCreateBody();

        mockMvc.perform(post("/api/v1/admin/albums")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT 단건 갱신 → 200 + stock 은 변하지 않음")
    void update_returns200_andDoesNotMutateStock() throws Exception {
        Album saved = persistedAlbum(7);

        Map<String, Object> body = validCreateBody();
        body.remove("stock"); // PUT 에는 stock 필드 없음
        body.put("title", "New Title");
        body.put("price", 50000);
        body.put("status", "HIDDEN");
        body.put("isLimited", true);

        mockMvc.perform(put("/api/v1/admin/albums/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.isLimited").value(true))
                .andExpect(jsonPath("$.stock").value(7));

        assertThat(albumRepository.findById(saved.getId()).orElseThrow().getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("PUT 미존재 album → 404 ALBUM_NOT_FOUND")
    void update_notFound_returns404() throws Exception {
        Map<String, Object> body = validCreateBody();
        body.remove("stock");

        mockMvc.perform(put("/api/v1/admin/albums/{id}", 99_999L)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE 단건 → 204 + DB 제거")
    void delete_returns204() throws Exception {
        Album saved = persistedAlbum(0);

        mockMvc.perform(delete("/api/v1/admin/albums/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNoContent());

        assertThat(albumRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE 미존재 album → 404 ALBUM_NOT_FOUND")
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/albums/{id}", 99_999L)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /stock 양수 delta → 200 + 재고 증가")
    void patchStock_positive_returns200() throws Exception {
        Album saved = persistedAlbum(5);

        mockMvc.perform(patch("/api/v1/admin/albums/{id}/stock", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(8));

        assertThat(albumRepository.findById(saved.getId()).orElseThrow().getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("PATCH /stock 음수 delta (가능) → 200 + 재고 감소")
    void patchStock_negative_returns200() throws Exception {
        Album saved = persistedAlbum(5);

        mockMvc.perform(patch("/api/v1/admin/albums/{id}/stock", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\": -3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(2));
    }

    @Test
    @DisplayName("PATCH /stock 결과 음수 → 400 ALBUM_INVALID_STOCK + 재고 불변")
    void patchStock_resultNegative_returns400() throws Exception {
        Album saved = persistedAlbum(2);

        mockMvc.perform(patch("/api/v1/admin/albums/{id}/stock", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALBUM_INVALID_STOCK"));

        assertThat(albumRepository.findById(saved.getId()).orElseThrow().getStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("PATCH /stock delta 누락 → 400")
    void patchStock_missingDelta_returns400() throws Exception {
        Album saved = persistedAlbum(0);

        mockMvc.perform(patch("/api/v1/admin/albums/{id}/stock", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /stock 미존재 album → 404")
    void patchStock_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/albums/{id}/stock", 99_999L)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /stock USER 권한 → 403")
    void patchStock_userRole_returns403() throws Exception {
        Album saved = persistedAlbum(0);

        mockMvc.perform(patch("/api/v1/admin/albums/{id}/stock", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\": 1}"))
                .andExpect(status().isForbidden());
    }

    private Album persistedAlbum(int initialStock) {
        Artist artist = artistRepository.findById(artistId).orElseThrow();
        Genre genre = genreRepository.findById(genreId).orElseThrow();
        Label label = labelRepository.findById(labelId).orElseThrow();
        return albumRepository.saveAndFlush(Album.create(
                "Abbey Road", artist, genre, label,
                (short) 1969, AlbumFormat.LP_12, 35000L, initialStock,
                AlbumStatus.SELLING, false, null, null));
    }
}
