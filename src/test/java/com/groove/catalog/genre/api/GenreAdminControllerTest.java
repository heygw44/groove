package com.groove.catalog.genre.api;

import com.groove.auth.security.JwtProvider;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/admin/genres (ADMIN CRUD)")
class GenreAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private String adminBearer;
    private String userBearer;

    @BeforeEach
    void cleanup() {
        // Album → Genre FK 때문에 album 을 먼저 비운다 (W5-3 도입).
        albumRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        adminBearer = "Bearer " + jwtProvider.issueAccessToken(1L, MemberRole.ADMIN);
        userBearer = "Bearer " + jwtProvider.issueAccessToken(2L, MemberRole.USER);
    }

    @Test
    @DisplayName("POST → 201 + Location + DB 영속")
    void create_returns201() throws Exception {
        Map<String, String> body = Map.of("name", "Rock");

        mockMvc.perform(post("/api/v1/admin/genres")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Rock"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        assertThat(genreRepository.findByName("Rock")).isPresent();
    }

    @Test
    @DisplayName("POST 중복 name → 409 + GENRE_NAME_DUPLICATED ProblemDetail")
    void create_duplicateName_returns409() throws Exception {
        genreRepository.saveAndFlush(Genre.create("Jazz"));

        Map<String, String> body = Map.of("name", "Jazz");

        mockMvc.perform(post("/api/v1/admin/genres")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("GENRE_NAME_DUPLICATED"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("POST 미인증 → 401")
    void create_unauthenticated_returns401() throws Exception {
        Map<String, String> body = Map.of("name", "Rock");

        mockMvc.perform(post("/api/v1/admin/genres")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST USER 권한 → 403")
    void create_withUserRole_returns403() throws Exception {
        Map<String, String> body = Map.of("name", "Rock");

        mockMvc.perform(post("/api/v1/admin/genres")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST 빈 name → 400 (Bean Validation)")
    void create_blankName_returns400() throws Exception {
        Map<String, String> body = Map.of("name", "  ");

        mockMvc.perform(post("/api/v1/admin/genres")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST 길이 51자 → 400 (max=50)")
    void create_overlongName_returns400() throws Exception {
        Map<String, String> body = Map.of("name", "a".repeat(51));

        mockMvc.perform(post("/api/v1/admin/genres")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET 목록 (ADMIN) → 200 + id ASC")
    void list_returnsAllSorted() throws Exception {
        genreRepository.saveAndFlush(Genre.create("Rock"));
        genreRepository.saveAndFlush(Genre.create("Jazz"));

        mockMvc.perform(get("/api/v1/admin/genres")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Rock"))
                .andExpect(jsonPath("$[1].name").value("Jazz"));
    }

    @Test
    @DisplayName("PUT 단건 갱신 → 200 + name 변경")
    void update_returns200() throws Exception {
        Genre saved = genreRepository.saveAndFlush(Genre.create("RnB"));

        Map<String, String> body = Map.of("name", "R&B");

        mockMvc.perform(put("/api/v1/admin/genres/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("R&B"));

        assertThat(genreRepository.findById(saved.getId()).orElseThrow().getName()).isEqualTo("R&B");
    }

    @Test
    @DisplayName("PUT 자기 자신과 동일 name → 200 (no-op)")
    void update_sameName_returns200() throws Exception {
        Genre saved = genreRepository.saveAndFlush(Genre.create("Jazz"));

        Map<String, String> body = Map.of("name", "Jazz");

        mockMvc.perform(put("/api/v1/admin/genres/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT 다른 id 가 사용 중인 name → 409")
    void update_nameTakenByAnother_returns409() throws Exception {
        Genre rock = genreRepository.saveAndFlush(Genre.create("Rock"));
        Genre jazz = genreRepository.saveAndFlush(Genre.create("Jazz"));

        Map<String, String> body = Map.of("name", "Rock");

        mockMvc.perform(put("/api/v1/admin/genres/{id}", jazz.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENRE_NAME_DUPLICATED"));
        // assert no change persisted
        assertThat(genreRepository.findById(rock.getId()).orElseThrow().getName()).isEqualTo("Rock");
        assertThat(genreRepository.findById(jazz.getId()).orElseThrow().getName()).isEqualTo("Jazz");
    }

    @Test
    @DisplayName("PUT 미존재 id → 404")
    void update_notFound_returns404() throws Exception {
        Map<String, String> body = Map.of("name", "Anything");

        mockMvc.perform(put("/api/v1/admin/genres/{id}", 9_999L)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENRE_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE 단건 → 204 + DB 제거")
    void delete_returns204() throws Exception {
        Genre saved = genreRepository.saveAndFlush(Genre.create("Pop"));

        mockMvc.perform(delete("/api/v1/admin/genres/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNoContent());

        assertThat(genreRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE 미존재 id → 404")
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/genres/{id}", 9_999L)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENRE_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT USER 권한 → 403")
    void update_userRole_returns403() throws Exception {
        Genre saved = genreRepository.saveAndFlush(Genre.create("Pop"));

        Map<String, String> body = Map.of("name", "Soul");

        mockMvc.perform(put("/api/v1/admin/genres/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE USER 권한 → 403 + 엔티티 보존")
    void delete_userRole_returns403() throws Exception {
        Genre saved = genreRepository.saveAndFlush(Genre.create("Pop"));

        mockMvc.perform(delete("/api/v1/admin/genres/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isForbidden());

        // SecurityFilter 우회로 실제 삭제까지 도달하는 회귀를 잡기 위한 DB 가드.
        assertThat(genreRepository.findById(saved.getId())).isPresent();
    }
}
