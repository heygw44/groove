package com.groove.catalog.artist.api;

import com.groove.auth.security.JwtProvider;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/admin/artists (ADMIN CRUD)")
class ArtistAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private String adminBearer;
    private String userBearer;

    @BeforeEach
    void cleanup() {
        // Album → Artist FK 때문에 album 을 먼저 비운다 (W5-3 도입).
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        adminBearer = "Bearer " + jwtProvider.issueAccessToken(1L, MemberRole.ADMIN);
        userBearer = "Bearer " + jwtProvider.issueAccessToken(2L, MemberRole.USER);
    }

    @Test
    @DisplayName("POST → 201 + Location + DB 영속")
    void create_returns201() throws Exception {
        Map<String, String> body = Map.of("name", "The Beatles", "description", "British rock band");

        mockMvc.perform(post("/api/v1/admin/artists")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("The Beatles"))
                .andExpect(jsonPath("$.description").value("British rock band"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        assertThat(artistRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST description 생략 → 201 + description 필드 null 응답")
    void create_withoutDescription_returns201() throws Exception {
        // null 값을 전송하기 위해 HashMap 사용 (Map.of 는 null 미허용)
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Anonymous");
        body.put("description", null);

        mockMvc.perform(post("/api/v1/admin/artists")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(nullValue()));
    }

    @Test
    @DisplayName("POST 동명이인 → 둘 다 201 (UNIQUE 미적용)")
    void create_duplicateName_allowed() throws Exception {
        Map<String, String> body = Map.of("name", "John");

        mockMvc.perform(post("/api/v1/admin/artists")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/artists")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        assertThat(artistRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("POST 미인증 → 401")
    void create_unauthenticated_returns401() throws Exception {
        Map<String, String> body = Map.of("name", "X");

        mockMvc.perform(post("/api/v1/admin/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST USER 권한 → 403")
    void create_withUserRole_returns403() throws Exception {
        Map<String, String> body = Map.of("name", "X");

        mockMvc.perform(post("/api/v1/admin/artists")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST 빈 name → 400")
    void create_blankName_returns400() throws Exception {
        Map<String, String> body = Map.of("name", "  ");

        mockMvc.perform(post("/api/v1/admin/artists")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST name 201자 → 400 (max=200)")
    void create_overlongName_returns400() throws Exception {
        Map<String, String> body = Map.of("name", "a".repeat(201));

        mockMvc.perform(post("/api/v1/admin/artists")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST description 2001자 → 400 (max=2000)")
    void create_overlongDescription_returns400() throws Exception {
        Map<String, String> body = Map.of("name", "X", "description", "a".repeat(2001));

        mockMvc.perform(post("/api/v1/admin/artists")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET 페이징 목록 (ADMIN) → 200 + envelope 형식")
    void list_returnsPageEnvelope() throws Exception {
        for (int i = 1; i <= 3; i++) {
            artistRepository.saveAndFlush(Artist.create("Artist " + i, null));
        }

        mockMvc.perform(get("/api/v1/admin/artists")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("PUT 단건 갱신 → 200 + name/description 동시 변경")
    void update_returns200() throws Exception {
        Artist saved = artistRepository.saveAndFlush(Artist.create("Old", "Old desc"));

        Map<String, String> body = Map.of("name", "New", "description", "New desc");

        mockMvc.perform(put("/api/v1/admin/artists/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.description").value("New desc"));

        Artist reloaded = artistRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("New");
        assertThat(reloaded.getDescription()).isEqualTo("New desc");
    }

    @Test
    @DisplayName("PUT description 을 null 로 보내면 명시적 지움 → 200")
    void update_clearsDescription() throws Exception {
        Artist saved = artistRepository.saveAndFlush(Artist.create("Name", "Old desc"));

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Name");
        body.put("description", null);

        mockMvc.perform(put("/api/v1/admin/artists/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()));

        assertThat(artistRepository.findById(saved.getId()).orElseThrow().getDescription()).isNull();
    }

    @Test
    @DisplayName("PUT 미존재 id → 404 ARTIST_NOT_FOUND")
    void update_notFound_returns404() throws Exception {
        Map<String, String> body = Map.of("name", "Anything");

        mockMvc.perform(put("/api/v1/admin/artists/{id}", 9_999L)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE 단건 → 204 + DB 제거")
    void delete_returns204() throws Exception {
        Artist saved = artistRepository.saveAndFlush(Artist.create("X", null));

        mockMvc.perform(delete("/api/v1/admin/artists/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNoContent());

        assertThat(artistRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE 미존재 id → 404 ARTIST_NOT_FOUND")
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/artists/{id}", 9_999L)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT USER 권한 → 403")
    void update_userRole_returns403() throws Exception {
        Artist saved = artistRepository.saveAndFlush(Artist.create("Old", null));

        Map<String, String> body = Map.of("name", "New");

        mockMvc.perform(put("/api/v1/admin/artists/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE USER 권한 → 403 + 엔티티 보존")
    void delete_userRole_returns403() throws Exception {
        Artist saved = artistRepository.saveAndFlush(Artist.create("X", null));

        mockMvc.perform(delete("/api/v1/admin/artists/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isForbidden());

        // SecurityFilter 우회로 실제 삭제까지 도달하는 회귀를 잡기 위한 DB 가드.
        assertThat(artistRepository.findById(saved.getId())).isPresent();
    }
}
