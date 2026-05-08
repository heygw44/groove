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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Album FK 가 활성화된 이후 Artist/Genre/Label 삭제가 album 참조 시 409 로 거절되는지 통합 검증.
 * 사전검사({@code existsByXxx_Id}) 경로가 작동함을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("카탈로그 부모 도메인 DELETE — album 참조 시 409 IN_USE")
class CatalogDeleteInUseTest {

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

    @Autowired
    private JwtProvider jwtProvider;

    private String adminBearer;

    @BeforeEach
    void setUp() {
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        adminBearer = "Bearer " + jwtProvider.issueAccessToken(1L, MemberRole.ADMIN);
    }

    private record Refs(Artist artist, Genre genre, Label label) {
    }

    private Refs persistAlbum() {
        Artist artist = artistRepository.saveAndFlush(Artist.create("A", null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("G"));
        Label label = labelRepository.saveAndFlush(Label.create("L"));
        albumRepository.saveAndFlush(Album.create(
                "T", artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, 0L, 0,
                AlbumStatus.SELLING, false, null, null));
        return new Refs(artist, genre, label);
    }

    @Test
    @DisplayName("DELETE artist → album 이 참조 중이면 409 ARTIST_IN_USE")
    void deleteArtist_inUse_returns409() throws Exception {
        Refs refs = persistAlbum();

        mockMvc.perform(delete("/api/v1/admin/artists/{id}", refs.artist().getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTIST_IN_USE"));
    }

    @Test
    @DisplayName("DELETE genre → album 이 참조 중이면 409 GENRE_IN_USE")
    void deleteGenre_inUse_returns409() throws Exception {
        Refs refs = persistAlbum();

        mockMvc.perform(delete("/api/v1/admin/genres/{id}", refs.genre().getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENRE_IN_USE"));
    }

    @Test
    @DisplayName("DELETE label → album 이 참조 중이면 409 LABEL_IN_USE")
    void deleteLabel_inUse_returns409() throws Exception {
        Refs refs = persistAlbum();

        mockMvc.perform(delete("/api/v1/admin/labels/{id}", refs.label().getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LABEL_IN_USE"));
    }
}
