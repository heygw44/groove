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

/**
 * <b>N+1 제거 회귀 가드</b> (#203, ERD §4.6 [W10]).
 *
 * <p>{@code GET /albums} 목록 응답이 artist/genre/label({@code @ManyToOne(LAZY)})을
 * {@link AlbumRepository#findAll} 의 {@code @EntityGraph} 로 동반 페치해 N+1 SELECT 를 제거했음을
 * Hibernate {@link Statistics} 로 고정한다. W9 베이스라인(#196)에서는 5행 조회 시 본 쿼리 1 +
 * 평점집계 1 + lazy resolve 15 = {@code prepareStatementCount 17}, {@code entityFetchCount 15}
 * (행 P개로 일반화 시 {@code 1 + 1 + 3P})였다.
 *
 * <p>개선 후 판정 기준: 페치 조인으로 {@code entityFetchCount == 0}, {@code prepareStatementCount}
 * 는 본 쿼리 1 + 평점집계 1 = <b>2 로 행수 무관 상수</b>. 누군가 {@code @EntityGraph} 를 제거해
 * N+1 을 재유발하면 본 테스트가 즉시 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("AlbumService.search — N+1 제거 회귀 가드 (#203)")
class AlbumQueryN1Test {

    /** 평점집계 IN 쿼리는 빈 페이지가 아니면 항상 실행되므로, 본 쿼리(1) + 평점집계(1) = 상수. */
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

        // artist/genre/label 이 본 쿼리에 OUTER JOIN 으로 인라인 → 행마다 풀리는 lazy fetch 가 0.
        assertThat(statistics.getEntityFetchCount())
                .as("@EntityGraph 동반 페치로 추가 lazy resolve 가 없어야 한다 (W9 베이스라인 15 → 0)")
                .isZero();
        // 본 쿼리 1 + 평점집계 1. 단일 페이지(5 < 20)라 count 쿼리는 스킵된다.
        assertThat(statistics.getPrepareStatementCount())
                .as("@EntityGraph 를 제거하면 lazy resolve 가 살아나 이 어서션이 깨진다 — N+1 재발 가드")
                .isEqualTo(EXPECTED_QUERY_COUNT);

        // 페치가 실제로 됐는지(프록시 null 아님) — 연관 값이 DTO 에 채워졌는지 확인.
        AlbumSummaryResponse first = page.getContent().getFirst();
        assertThat(first.artist()).isNotNull();
        assertThat(first.artist().name()).isNotNull();
        assertThat(first.genre()).isNotNull();
        assertThat(first.genre().name()).isNotNull();
        assertThat(first.label()).isNotNull();
        assertThat(first.label().name()).isNotNull();
    }

    /**
     * <b>행수 무관 상수화 증명</b> — 베이스라인의 {@code 1 + 1 + 3P} 선형 증가가 제거됐음을 직접 보인다.
     * 행 수를 3건과 7건으로 달리해도 {@code prepareStatementCount} 가 동일(2)함을 단언한다.
     */
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

    /**
     * <b>keyword 경로 + label=null 회귀 가드</b> — {@code AlbumSpecs.keyword} 가 WHERE 용
     * {@code artist LEFT JOIN} 을 추가하는 경로에서 {@code @EntityGraph} 페치 조인과 겹쳐도
     * (artist 조인 2개) N+1 이 없고, {@code label=null} 앨범이 OUTER JOIN 으로 결과에서 누락되지
     * 않음을 함께 고정한다. SELLING_ALL 경로만 보면 이 조합이 한 번도 실행되지 않아 생기는 갭을 메운다.
     */
    @Test
    @DisplayName("[#203] keyword 검색 + label=null 혼합 — artist 이중조인에도 N+1 없음, null 행 누락 없음")
    void search_withKeywordAndNullLabel_noN1() {
        seedKeywordAlbums();

        AlbumSearchCondition cond = new AlbumSearchCondition(
                "Groove", null, null, null, null, null, null, null, null, null, AlbumStatus.SELLING);
        Page<AlbumSummaryResponse> page = albumService.search(cond, PageRequest.of(0, 20));

        // keyword 의 artist LEFT JOIN + @EntityGraph 의 artist fetch 조인이 겹쳐도 lazy resolve 0.
        assertThat(statistics.getEntityFetchCount())
                .as("keyword 경로에서도 artist/genre/label 이 본 쿼리에 동반 페치돼야 한다")
                .isZero();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(EXPECTED_QUERY_COUNT);

        // label=null 앨범이 LEFT OUTER JOIN 으로 결과에 포함(누락 없음) — 3건 중 1건은 label 없음.
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
                .as("label=null 앨범도 결과에 포함돼야 한다 (INNER JOIN 이면 누락)")
                .anyMatch(a -> a.label() == null)
                .allMatch(a -> a.artist() != null && a.genre() != null);
    }

    /** 주어진 행 수로 재적재 후 search 1회의 prepareStatementCount 를 측정한다. */
    private long measureQueryCountForRows(int rows) {
        seedAlbums(rows);

        Page<AlbumSummaryResponse> page = albumService.search(SELLING_ALL, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(rows);
        assertThat(statistics.getEntityFetchCount()).isZero();
        return statistics.getPrepareStatementCount();
    }

    /**
     * 공유 Testcontainers DB 격리: 전체 wipe 후 정확히 {@code count} 건 적재한다. 각 album 이 unique
     * artist/genre/label 을 참조하도록 해 1차 캐시 hit 으로 측정이 흐려지는 것을 막고, 적재 후
     * {@link EntityManager#clear()} 로 영속성 컨텍스트를 비운 뒤 {@link Statistics#clear()} 로
     * 측정을 0 에서 시작한다 — search 호출만 통계에 잡히도록.
     */
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

    /**
     * keyword("Groove") 로 전부 매칭되는 3건을 unique artist/genre 로 적재하되, 첫 행은
     * {@code label=null} 로 둔다 — keyword 의 artist 조인 + {@code @EntityGraph} 페치 조인이
     * 겹치는 경로와 nullable label 의 OUTER JOIN 동작을 한 번에 커버하기 위함.
     */
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
