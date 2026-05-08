package com.groove.catalog.artist.api;

import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("GET /api/v1/artists (Public)")
class ArtistQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ArtistRepository artistRepository;

    @BeforeEach
    void cleanup() {
        artistRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("미인증 GET 목록 → 200 + 페이징 envelope (기본값 page=0, size=20)")
    void list_publicAccess_returnsPageEnvelopeWithDefaults() throws Exception {
        artistRepository.saveAndFlush(Artist.create("A1", null));
        artistRepository.saveAndFlush(Artist.create("A2", "desc"));

        mockMvc.perform(get("/api/v1/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("A1"))
                .andExpect(jsonPath("$.content[1].name").value("A2"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("page/size/sort 쿼리 파라미터 적용 → 200")
    void list_appliesPageSizeSortParameters() throws Exception {
        for (int i = 1; i <= 5; i++) {
            artistRepository.saveAndFlush(Artist.create("Artist " + i, null));
        }

        mockMvc.perform(get("/api/v1/artists")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "id,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    @DisplayName("빈 목록 → 200 + content 빈 배열, totalElements=0")
    void list_empty_returnsEmptyEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("GET 단건 → 200 + ArtistResponse")
    void get_returnsArtist() throws Exception {
        Artist saved = artistRepository.saveAndFlush(Artist.create("Solo", "desc"));

        mockMvc.perform(get("/api/v1/artists/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Solo"))
                .andExpect(jsonPath("$.description").value("desc"));
    }

    @Test
    @DisplayName("GET 미존재 id → 404 ARTIST_NOT_FOUND")
    void get_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/artists/{id}", 9_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
    }
}
