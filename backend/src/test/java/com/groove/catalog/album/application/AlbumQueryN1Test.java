package com.groove.catalog.album.application;

import com.groove.catalog.album.api.dto.AlbumSummaryResponse;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// N+1 제거 회귀 가드.
// GET /albums 목록 응답이 artist/genre/label 을 findAll 의 @EntityGraph 로 동반 페치해 N+1 SELECT 가
// 없음을 Hibernate Statistics 로 고정한다.
// 판정 기준: entityFetchCount == 0, prepareStatementCount 는 본 쿼리 1 + 평점집계 1 = 2 로 행수 무관 상수.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("AlbumService.search — N+1 제거 회귀 가드 (#203)")
class AlbumQueryN1Test {

    // 본 쿼리(1) + 평점집계(1) = 상수.
    private static final long EXPECTED_QUERY_COUNT = 2L;

    @Autowired
    private AlbumService albumService;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;

    private static final AlbumSearchCondition SELLING_ALL = new AlbumSearchCondition(
            null, null, null, null, null, null, null, null, null, null, AlbumStatus.SELLING);

    @BeforeEach
    void setUp() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    @DisplayName("5건 SELLING 검색 — @EntityGraph 페치 조인으로 lazy resolve 0, 쿼리 2개")
    void search_fetchesAssociationsInOneQuery_noN1() {
        seedAlbums(5);

        Page<AlbumSummaryResponse> page = albumService.search(SELLING_ALL, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(5);

        // artist/genre/label 이 본 쿼리에 OUTER JOIN 으로 인라인 → lazy fetch 0
        assertThat(statistics.getEntityFetchCount())
                .as("@EntityGraph 동반 페치로 추가 lazy resolve 가 없어야 한다 (W9 베이스라인 15 → 0)")
                .isZero();
        // 본 쿼리 1 + 평점집계 1. 단일 페이지(5 < 20)라 count 쿼리는 스킵된다.
        assertThat(statistics.getPrepareStatementCount())
                .as("@EntityGraph 를 제거하면 lazy resolve 가 살아나 이 어서션이 깨진다 — N+1 재발 가드")
                .isEqualTo(EXPECTED_QUERY_COUNT);

        // 연관 값이 DTO 에 채워졌는지 확인
        AlbumSummaryResponse first = page.getContent().getFirst();
        assertThat(first.artist()).isNotNull();
        assertThat(first.artist().name()).isNotNull();
        assertThat(first.genre()).isNotNull();
        assertThat(first.genre().name()).isNotNull();
        assertThat(first.label()).isNotNull();
        assertThat(first.label().name()).isNotNull();
    }

    // 행 수를 3건과 7건으로 달리해도 prepareStatementCount 가 동일(2)함을 단언한다.
    @Test
    @DisplayName("[#203] 행수가 달라도 쿼리 수 상수 — 3건·7건 모두 2개")
    void search_keepsQueryCountConstant_regardlessOfRowCount() {
        long count3 = measureQueryCountForRows(3);
        long count7 = measureQueryCountForRows(7);

        System.out.printf(
                "[#203 N+1 after] rows=3 → prepareStatementCount=%d, rows=7 → prepareStatementCount=%d "
                        + "(entityFetchCount=0, queryExecutionCount=2)%n",
                count3, count7);

        assertThat(count3)
                .as("행수가 늘어도 쿼리 수가 선형 증가하지 않아야 한다 (베이스라인 1+1+3P → 상수 2)")
                .isEqualTo(count7)
                .isEqualTo(EXPECTED_QUERY_COUNT);
    }

    // keyword(FULLTEXT) 검색 경로에서도 @EntityGraph 동반 페치로 N+1 이 없고, label=null 앨범이
    // OUTER JOIN 으로 결과에서 누락되지 않음을 검증한다.
    @Test
    @DisplayName("[#203·#204] keyword(FULLTEXT) 검색 + label=null 혼합 — N+1 없음, null 행 누락 없음")
    void search_withKeywordAndNullLabel_noN1() {
        seedKeywordAlbums();

        AlbumSearchCondition cond = new AlbumSearchCondition(
                "Groove", null, null, null, null, null, null, null, null, null, AlbumStatus.SELLING);
        Page<AlbumSummaryResponse> page = albumService.search(cond, PageRequest.of(0, 20));

        // keyword(FULLTEXT) 경로에서도 @EntityGraph 동반 페치로 lazy resolve 0
        assertThat(statistics.getEntityFetchCount())
                .as("keyword 경로에서도 artist/genre/label 이 본 쿼리에 동반 페치돼야 한다")
                .isZero();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(EXPECTED_QUERY_COUNT);

        // label=null 앨범이 LEFT OUTER JOIN 으로 결과에 포함 — 3건 중 1건은 label 없음
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
                .as("label=null 앨범도 결과에 포함돼야 한다 (INNER JOIN 이면 누락)")
                .anyMatch(a -> a.label() == null)
                .allMatch(a -> a.artist() != null && a.genre() != null);
    }

    // 주어진 행 수로 재적재 후 search 1회의 prepareStatementCount 를 측정한다.
    private long measureQueryCountForRows(int rows) {
        seedAlbums(rows);

        Page<AlbumSummaryResponse> page = albumService.search(SELLING_ALL, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(rows);
        assertThat(statistics.getEntityFetchCount()).isZero();
        return statistics.getPrepareStatementCount();
    }

    // 전체 wipe 후 정확히 count 건 적재한다. 각 album 이 unique artist/genre/label 을 참조하도록 하고,
    // 적재 후 영속성 컨텍스트와 통계를 비워 search 호출만 통계에 잡히도록 한다.
    private void seedAlbums(int count) {
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();

        for (int i = 0; i < count; i++) {
            Artist artist = artistRepository.saveAndFlush(Artist.create("Artist " + i, null));
            Genre genre = genreRepository.saveAndFlush(Genre.create("Genre " + i));
            Label label = labelRepository.saveAndFlush(Label.create("Label " + i));
            albumRepository.saveAndFlush(Album.create(
                    "Album " + i, artist, genre, label,
                    (short) (1965 + i), AlbumFormat.LP_12, 30000L, 5,
                    AlbumStatus.SELLING, false, null, null));
        }

        entityManager.clear();
        statistics.clear();
    }

    // keyword("Groove") 로 전부 매칭되는 3건을 unique artist/genre 로 적재하되, 첫 행은 label=null 로 둔다.
    private void seedKeywordAlbums() {
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();

        for (int i = 0; i < 3; i++) {
            Artist artist = artistRepository.saveAndFlush(Artist.create("Artist " + i, null));
            Genre genre = genreRepository.saveAndFlush(Genre.create("Genre " + i));
            Label label = (i == 0) ? null
                    : labelRepository.saveAndFlush(Label.create("Label " + i));
            albumRepository.saveAndFlush(Album.create(
                    "Groove Hits " + i, artist, genre, label,
                    (short) (1970 + i), AlbumFormat.LP_12, 30000L, 5,
                    AlbumStatus.SELLING, false, null, null));
        }

        entityManager.clear();
        statistics.clear();
    }
}
