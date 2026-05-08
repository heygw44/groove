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
import org.junit.jupiter.api.Nested;
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
 * /api/v1/albums (Public 목록·검색·상세) MockMvc 통합 테스트 (#34).
 *
 * <p>의도적 N+1 보존 검증은 별도 Hibernate Statistics 테스트(
 * {@code AlbumQueryN1Test})에서 수행한다. 본 테스트는 응답 envelope·필터·정렬·페이징·검증 위주.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("GET /api/v1/albums (Public 검색·상세)")
class AlbumQueryControllerTest {

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
    private Genre jazz;
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
        jazz = genreRepository.saveAndFlush(Genre.create("Jazz"));
        apple = labelRepository.saveAndFlush(Label.create("Apple Records"));
    }

    @Nested
    @DisplayName("GET /albums — 목록·검색·필터·정렬")
    class List {

        @Test
        @DisplayName("기본 → 200 + PageResponse envelope (size=20, sort=createdAt,desc)")
        void defaults_returnsPageEnvelope() throws Exception {
            persistAlbum("Abbey Road", beatles, rock, apple, (short) 1969, 35000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("Let It Be", beatles, rock, apple, (short) 1970, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);

            mockMvc.perform(get("/api/v1/albums"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.last").value(true));
        }

        @Test
        @DisplayName("HIDDEN 앨범 → SELLING 강제 적용으로 목록에서 제외")
        void hiddenStatus_isExcludedByDefault() throws Exception {
            persistAlbum("Public", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("Hidden", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.HIDDEN);

            mockMvc.perform(get("/api/v1/albums"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Public"));
        }

        @Test
        @DisplayName("status=HIDDEN 명시 요청 → 400 (Public 차단)")
        void requestingHiddenStatus_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/albums").param("status", "HIDDEN"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALID_001"));
        }

        @Test
        @DisplayName("status=SOLD_OUT 명시 → 200 + 해당 상태만 반환")
        void requestingSoldOutStatus_returnsOnlySoldOut() throws Exception {
            persistAlbum("Selling", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("SoldOut", beatles, rock, apple, (short) 1970, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SOLD_OUT);

            mockMvc.perform(get("/api/v1/albums").param("status", "SOLD_OUT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("SoldOut"));
        }

        @Test
        @DisplayName("genreId 필터 → 200 + 해당 장르만 반환")
        void filter_byGenreId() throws Exception {
            persistAlbum("RockAlbum", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("JazzAlbum", beatles, jazz, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);

            mockMvc.perform(get("/api/v1/albums").param("genreId", String.valueOf(jazz.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("JazzAlbum"))
                    .andExpect(jsonPath("$.content[0].genre.id").value(jazz.getId()));
        }

        @Test
        @DisplayName("artistId 필터 → 200 + 해당 아티스트만")
        void filter_byArtistId() throws Exception {
            persistAlbum("Beatles1", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("Stones1", stones, rock, apple, (short) 1971, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);

            mockMvc.perform(get("/api/v1/albums").param("artistId", String.valueOf(stones.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Stones1"));
        }

        @Test
        @DisplayName("price 범위 필터 → 200 + 양 끝 inclusive")
        void filter_byPriceRange() throws Exception {
            persistAlbum("Cheap", beatles, rock, apple, (short) 1969, 10000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("Mid", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("Expensive", beatles, rock, apple, (short) 1969, 90000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);

            mockMvc.perform(get("/api/v1/albums")
                            .param("minPrice", "20000")
                            .param("maxPrice", "50000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Mid"));
        }

        @Test
        @DisplayName("year 범위 필터 → 200 + 양 끝 inclusive")
        void filter_byYearRange() throws Exception {
            persistAlbum("Old", beatles, rock, apple, (short) 1965, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("Mid", beatles, rock, apple, (short) 1970, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("Recent", beatles, rock, apple, (short) 2020, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);

            mockMvc.perform(get("/api/v1/albums")
                            .param("minYear", "1969")
                            .param("maxYear", "1971"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Mid"));
        }

        @Test
        @DisplayName("format 필터 → 200")
        void filter_byFormat() throws Exception {
            persistAlbum("LP", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("EP", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.EP, false, AlbumStatus.SELLING);

            mockMvc.perform(get("/api/v1/albums").param("format", "EP"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("EP"));
        }

        @Test
        @DisplayName("isLimited=true 필터 → 한정반만")
        void filter_byIsLimited() throws Exception {
            persistAlbum("Std", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("Ltd", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, true, AlbumStatus.SELLING);

            mockMvc.perform(get("/api/v1/albums").param("isLimited", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Ltd"));
        }

        @Test
        @DisplayName("keyword → title 또는 artist.name LIKE OR 매칭")
        void filter_byKeywordOrAlbumTitleOrArtistName() throws Exception {
            persistAlbum("Abbey Road", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("Some Stones Album", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("Aftermath", stones, rock, apple, (short) 1966, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);

            mockMvc.perform(get("/api/v1/albums").param("keyword", "stones"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @DisplayName("keyword 의 LIKE 메타문자(%/_) 는 escape 되어 와일드카드로 동작하지 않음")
        void filter_keywordEscapesLikeMetaCharacters() throws Exception {
            persistAlbum("Abbey Road", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("50% Off", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);

            // ?keyword=% 가 모든 행에 매칭되면 escape 미적용 — 정확히 "%" 를 포함한 행만 매칭되어야 한다.
            mockMvc.perform(get("/api/v1/albums").param("keyword", "%"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("50% Off"));
        }

        @Test
        @DisplayName("정렬 키 price,asc → 200 + 오름차순")
        void sort_priceAsc() throws Exception {
            persistAlbum("Mid", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("Cheap", beatles, rock, apple, (short) 1969, 10000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("High", beatles, rock, apple, (short) 1969, 50000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);

            mockMvc.perform(get("/api/v1/albums").param("sort", "price,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title").value("Cheap"))
                    .andExpect(jsonPath("$.content[1].title").value("Mid"))
                    .andExpect(jsonPath("$.content[2].title").value("High"));
        }

        @Test
        @DisplayName("화이트리스트에 없는 정렬 키 → 400")
        void sort_invalidKey_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/albums").param("sort", "title,asc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALID_001"));
        }

        @Test
        @DisplayName("page=1 size=1 → 200 + 두 번째 페이지 1건 + last=true")
        void paging_sizeBoundary() throws Exception {
            persistAlbum("A", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);
            persistAlbum("B", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);

            mockMvc.perform(get("/api/v1/albums")
                            .param("page", "1")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.first").value(false))
                    .andExpect(jsonPath("$.last").value(true));
        }

        @Test
        @DisplayName("음수 minPrice → 400")
        void filter_negativeMinPrice_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/albums").param("minPrice", "-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("maxYear=999999 (short 범위 초과) → 400")
        void filter_yearOverShortMax_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/albums").param("maxYear", "999999"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("응답에 averageRating(null) / reviewCount(0) placeholder 포함 (W7 도입 전)")
        void response_includesReviewPlaceholderFields() throws Exception {
            persistAlbum("A", beatles, rock, apple, (short) 1969, 30000L, AlbumFormat.LP_12, false, AlbumStatus.SELLING);

            mockMvc.perform(get("/api/v1/albums"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].averageRating").doesNotExist())
                    .andExpect(jsonPath("$.content[0].reviewCount").value(0));
        }
    }

    @Nested
    @DisplayName("GET /albums/{id} — 상세")
    class Detail {

        @Test
        @DisplayName("존재 id → 200 + AlbumDetail (description / createdAt / artist.description 포함)")
        void get_returnsDetail() throws Exception {
            Album saved = persistAlbumWithDetails("Abbey Road", "Mastered from the original tapes",
                    "Liverpool quartet");

            mockMvc.perform(get("/api/v1/albums/{id}", saved.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(saved.getId()))
                    .andExpect(jsonPath("$.title").value("Abbey Road"))
                    .andExpect(jsonPath("$.description").value("Mastered from the original tapes"))
                    .andExpect(jsonPath("$.artist.description").value("Liverpool quartet"))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.reviewCount").value(0));
        }

        @Test
        @DisplayName("미존재 id → 404 ALBUM_NOT_FOUND")
        void get_notFound_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/albums/{id}", 99_999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ALBUM_NOT_FOUND"));
        }

        @Test
        @DisplayName("HIDDEN 앨범도 단건 GET 은 200 (관리자가 직접 URL 줄 수 있어야 함)")
        void get_hiddenAlbum_returns200() throws Exception {
            Album hidden = persistAlbum("Hidden", beatles, rock, apple, (short) 1969, 30000L,
                    AlbumFormat.LP_12, false, AlbumStatus.HIDDEN);

            mockMvc.perform(get("/api/v1/albums/{id}", hidden.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("HIDDEN"));
        }

        @Test
        @DisplayName("0 또는 음수 id → 400")
        void get_negativeId_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/albums/{id}", -1L))
                    .andExpect(status().isBadRequest());
        }
    }

    private Album persistAlbum(String title, Artist artist, Genre genre, Label label,
                               short year, long price, AlbumFormat format, boolean limited,
                               AlbumStatus status) {
        return albumRepository.saveAndFlush(Album.create(
                title, artist, genre, label, year, format, price, 10,
                status, limited, null, null));
    }

    private Album persistAlbumWithDetails(String title, String description, String artistDescription) {
        Artist artistWithDesc = artistRepository.saveAndFlush(Artist.create("Artist X", artistDescription));
        return albumRepository.saveAndFlush(Album.create(
                title, artistWithDesc, rock, apple, (short) 1969, AlbumFormat.LP_12, 30000L, 5,
                AlbumStatus.SELLING, false, "https://cover", description));
    }
}
