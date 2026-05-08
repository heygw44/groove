package com.groove.catalog.label.api;

import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
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
@DisplayName("GET /api/v1/labels (Public)")
class LabelQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @BeforeEach
    void cleanup() {
        albumRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("미인증 GET → 200 + 공개 응답")
    void list_publicAccess_returns200() throws Exception {
        labelRepository.saveAndFlush(Label.create("Apple"));
        labelRepository.saveAndFlush(Label.create("Motown"));

        mockMvc.perform(get("/api/v1/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Apple"))
                .andExpect(jsonPath("$[1].name").value("Motown"));
    }
}
