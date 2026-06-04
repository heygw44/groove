package com.groove.catalog.artist.api;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/artists/{id}/albums (#34) MockMvc 통합 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("GET /api/v1/artists/{id}/albums (Public)")
class ArtistAlbumsQueryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private LabelRepository labelRepository;

    private Artist beatles;
    private Artist stones;
    private Genre rock;
    private Label apple;

    @BeforeEach
    void setUp() {
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();

        beatles = artistRepository.saveAndFlush(Artist.create("The Beatles", null));
        stones = artistRepository.saveAndFlush(Artist.create("The Rolling Stones", null));
        rock = genreRepository.saveAndFlush(Genre.create("Rock"));
        apple = labelRepository.saveAndFlush(Label.create("Apple Records"));
    }

    @Test
    @DisplayName("존재 artist → 해당 artist 의 SELLING 앨범만 반환")
    void list_filtersByArtistAndDefaultsToSelling() throws Exception {
        persistAlbum("Beatles1", beatles, AlbumStatus.SELLING);
        persistAlbum("Beatles2", beatles, AlbumStatus.SELLING);
        persistAlbum("BeatlesHidden", beatles, AlbumStatus.HIDDEN);
        persistAlbum("Stones1", stones, AlbumStatus.SELLING);

        mockMvc.perform(get("/api/v1/artists/{id}/albums", beatles.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("미존재 artist → 404 ARTIST_NOT_FOUND")
    void list_unknownArtist_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/artists/{id}/albums", 99_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
    }

    @Test
    @DisplayName("HIDDEN 명시 요청 → 400 (Public 차단)")
    void list_requestingHidden_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/artists/{id}/albums", beatles.getId())
                        .param("status", "HIDDEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    @Test
    @DisplayName("정렬 화이트리스트 위반 → 400")
    void list_invalidSort_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/artists/{id}/albums", beatles.getId())
                        .param("sort", "title,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("path 의 id 와 다른 ?artistId 동시 지정 → 400 (silent override 방지)")
    void list_artistIdConflict_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/artists/{id}/albums", beatles.getId())
                        .param("artistId", String.valueOf(stones.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    @Test
    @DisplayName("path 의 id 와 동일한 ?artistId 명시 → 200 (혼용 허용)")
    void list_artistIdMatchingPath_returns200() throws Exception {
        persistAlbum("Beatles1", beatles, AlbumStatus.SELLING);

        mockMvc.perform(get("/api/v1/artists/{id}/albums", beatles.getId())
                        .param("artistId", String.valueOf(beatles.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("페이징 적용 → 200")
    void list_appliesPaging() throws Exception {
        for (int i = 0; i < 5; i++) {
            persistAlbum("Album" + i, beatles, AlbumStatus.SELLING);
        }

        mockMvc.perform(get("/api/v1/artists/{id}/albums", beatles.getId())
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    private Album persistAlbum(String title, Artist artist, AlbumStatus status) {
        return albumRepository.saveAndFlush(Album.create(
                title, artist, rock, apple, (short) 1969, AlbumFormat.LP_12, 30000L, 5,
                status, false, null, null));
    }
}
