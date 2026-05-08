package com.groove.catalog.label.api;

import com.groove.auth.security.JwtProvider;
import com.groove.catalog.album.domain.AlbumRepository;
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
@DisplayName("/api/v1/admin/labels (ADMIN CRUD)")
class LabelAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private String adminBearer;
    private String userBearer;

    @BeforeEach
    void cleanup() {
        // Album → Label FK 때문에 album 을 먼저 비운다 (W5-3 도입).
        albumRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        adminBearer = "Bearer " + jwtProvider.issueAccessToken(1L, MemberRole.ADMIN);
        userBearer = "Bearer " + jwtProvider.issueAccessToken(2L, MemberRole.USER);
    }

    @Test
    @DisplayName("POST → 201 + Location + DB 영속")
    void create_returns201() throws Exception {
        Map<String, String> body = Map.of("name", "Apple Records");

        mockMvc.perform(post("/api/v1/admin/labels")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Apple Records"));

        assertThat(labelRepository.findByName("Apple Records")).isPresent();
    }

    @Test
    @DisplayName("POST 중복 name → 409 + LABEL_NAME_DUPLICATED")
    void create_duplicateName_returns409() throws Exception {
        labelRepository.saveAndFlush(Label.create("Motown"));

        Map<String, String> body = Map.of("name", "Motown");

        mockMvc.perform(post("/api/v1/admin/labels")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("LABEL_NAME_DUPLICATED"));
    }

    @Test
    @DisplayName("POST 미인증 → 401, USER → 403")
    void create_authorizationBoundaries() throws Exception {
        Map<String, String> body = Map.of("name", "Blue Note");

        mockMvc.perform(post("/api/v1/admin/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/labels")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST 길이 101자 → 400 (max=100)")
    void create_overlongName_returns400() throws Exception {
        Map<String, String> body = Map.of("name", "a".repeat(101));

        mockMvc.perform(post("/api/v1/admin/labels")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT 단건 갱신 → 200 + name 변경 + DB 반영")
    void update_returns200() throws Exception {
        Label saved = labelRepository.saveAndFlush(Label.create("Sub Pop"));

        Map<String, String> body = Map.of("name", "Sub Pop Records");

        mockMvc.perform(put("/api/v1/admin/labels/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Sub Pop Records"));

        assertThat(labelRepository.findById(saved.getId()).orElseThrow().getName())
                .isEqualTo("Sub Pop Records");
    }

    @Test
    @DisplayName("PUT 자기 자신과 동일 name → 200 (no-op)")
    void update_sameName_returns200() throws Exception {
        Label saved = labelRepository.saveAndFlush(Label.create("Motown"));

        Map<String, String> body = Map.of("name", "Motown");

        mockMvc.perform(put("/api/v1/admin/labels/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT 다른 id name 충돌 → 409")
    void update_nameTakenByAnother_returns409() throws Exception {
        labelRepository.saveAndFlush(Label.create("Apple"));
        Label motown = labelRepository.saveAndFlush(Label.create("Motown"));

        Map<String, String> body = Map.of("name", "Apple");

        mockMvc.perform(put("/api/v1/admin/labels/{id}", motown.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LABEL_NAME_DUPLICATED"));
    }

    @Test
    @DisplayName("PUT 미존재 id → 404")
    void update_notFound_returns404() throws Exception {
        Map<String, String> body = Map.of("name", "Anything");

        mockMvc.perform(put("/api/v1/admin/labels/{id}", 9_999L)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LABEL_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE → 204, 미존재 → 404")
    void delete_returns204_orNotFound() throws Exception {
        Label saved = labelRepository.saveAndFlush(Label.create("Sub Pop"));

        mockMvc.perform(delete("/api/v1/admin/labels/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNoContent());

        assertThat(labelRepository.findById(saved.getId())).isEmpty();

        mockMvc.perform(delete("/api/v1/admin/labels/{id}", 9_999L)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET 목록 (ADMIN) → 200 + id ASC")
    void list_returnsAllSorted() throws Exception {
        labelRepository.saveAndFlush(Label.create("Apple"));
        labelRepository.saveAndFlush(Label.create("Motown"));

        mockMvc.perform(get("/api/v1/admin/labels")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Apple"))
                .andExpect(jsonPath("$[1].name").value("Motown"));
    }
}
