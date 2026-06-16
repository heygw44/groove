package com.groove.catalog.album.api;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/albums/scroll (keyset 커서 페이징) MockMvc 통합 테스트 (#235).
 *
 * <p>헤드라인 AC: 동일 정렬키(같은 price)가 여럿이어도 전 페이지를 walk 했을 때 누락·중복 없이 전체를
 * 정확히 한 번씩 돌려준다(id tiebreaker 로 전순서 보장). 기존 offset 엔드포인트 회귀는
 * {@code AlbumQueryControllerTest} 가 담당한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("GET /api/v1/albums/scroll (keyset 커서 페이징)")
class AlbumScrollPagingTest {

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

    private Artist artist;
    private Genre genre;
    private Label label;

    @BeforeEach
    void setUp() {
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();

        artist = artistRepository.saveAndFlush(Artist.create("The Beatles", null));
        genre = genreRepository.saveAndFlush(Genre.create("Rock"));
        label = labelRepository.saveAndFlush(Label.create("Apple Records"));
    }

    @Test
    @DisplayName("첫 페이지(커서 없음) → size 만큼 반환 + hasNext=true + nextCursor 존재")
    void firstPage_returnsWindowWithCursor() throws Exception {
        persistAlbum("A", 30000L);
        persistAlbum("B", 30000L);
        persistAlbum("C", 30000L);

        mockMvc.perform(get("/api/v1/albums/scroll").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.totalElements").doesNotExist()); // keyset 은 전체 카운트 없음
    }

    @Test
    @DisplayName("마지막 페이지 → hasNext=false + nextCursor=null")
    void lastPage_hasNoCursor() throws Exception {
        persistAlbum("A", 30000L);
        persistAlbum("B", 30000L);

        mockMvc.perform(get("/api/v1/albums/scroll").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("[헤드라인 AC] 동일 price 5건을 size=2 로 walk → 누락·중복 없이 전체 5건")
    void walk_withDuplicateSortKey_coversAllExactlyOnce() throws Exception {
        List<Long> expected = new ArrayList<>();
        expected.add(persistAlbum("A", 30000L));
        expected.add(persistAlbum("B", 30000L));
        expected.add(persistAlbum("C", 30000L));
        expected.add(persistAlbum("D", 30000L));
        expected.add(persistAlbum("E", 30000L));

        List<Long> collected = walkAll("price,asc", 2);

        // 동일 price → id ASC tiebreaker 로 전순서 = 삽입 순(id 오름차순). 집합뿐 아니라 순서까지
        // 단언해 본 기능의 헤드라인 AC 인 "정렬 결정성"을 실제로 검증한다(누락·중복 + 순서).
        assertThat(collected).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("기본 정렬(createdAt,desc) walk 도 누락·중복 없이 전체 커버")
    void walk_defaultSort_coversAll() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            expected.add(persistAlbum("Album " + i, 10000L + i));
        }

        List<Long> collected = walkAll(null, 3);

        // 기본 정렬 createdAt DESC + id DESC tiebreaker → 삽입 역순. 순서까지 단언.
        List<Long> expectedDesc = new ArrayList<>(expected);
        Collections.reverse(expectedDesc);
        assertThat(collected).containsExactlyElementsOf(expectedDesc);
    }

    @Test
    @DisplayName("잘못된 커서 → 400 VALID_001")
    void invalidCursor_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/albums/scroll").param("cursor", "not-a-valid-cursor!!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    @Test
    @DisplayName("커서를 다른 정렬로 재사용 → 400 VALID_001 (500 누수 차단, #235 리뷰)")
    void cursorWithMismatchedSort_returns400() throws Exception {
        persistAlbum("A", 30000L);
        persistAlbum("B", 30000L);
        persistAlbum("C", 30000L);

        // 1페이지를 createdAt 정렬로 받아 nextCursor 확보(키={createdAt,id}).
        String cursor = scroll(null, 2, "createdAt,desc").get("nextCursor").asString();

        // 같은 커서를 price 정렬로 재요청 → 커서 키와 활성 정렬 키 불일치. 검증 없으면 Spring Data 가
        // keyset 술어 생성 시 IllegalStateException(→500)을 던지므로, 400 으로 막혀야 한다.
        mockMvc.perform(get("/api/v1/albums/scroll")
                        .param("cursor", cursor)
                        .param("size", "2")
                        .param("sort", "price,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    @Test
    @DisplayName("status=HIDDEN → 400 (Public 차단, offset 과 동일 정책)")
    void hiddenStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/albums/scroll").param("status", "HIDDEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    @Test
    @DisplayName("화이트리스트에 없는 정렬 키 → 400")
    void invalidSortKey_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/albums/scroll").param("sort", "title,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    /** nextCursor 가 없을 때까지 페이지를 이어 받아 id 를 순서대로 수집한다(무한 루프 가드 포함). */
    private List<Long> walkAll(String sort, int size) throws Exception {
        List<Long> collected = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 100; guard++) {
            JsonNode page = scroll(cursor, size, sort);
            for (JsonNode item : page.get("content")) {
                collected.add(item.get("id").asLong());
            }
            if (!page.get("hasNext").asBoolean()) {
                return collected;
            }
            cursor = page.get("nextCursor").asString();
        }
        throw new AssertionError("스크롤이 종료되지 않음 — 무한 루프 의심");
    }

    private JsonNode scroll(String cursor, int size, String sort) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/albums/scroll").param("size", String.valueOf(size));
        if (sort != null) {
            request = request.param("sort", sort);
        }
        if (cursor != null) {
            request = request.param("cursor", cursor);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Long persistAlbum(String title, long price) {
        return albumRepository.saveAndFlush(Album.create(
                title, artist, genre, label, (short) 1969, AlbumFormat.LP_12, price, 10,
                AlbumStatus.SELLING, false, null, null)).getId();
    }
}
