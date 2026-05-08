package com.groove.catalog.album.application;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>의도적 N+1 보존 시연 자료</b> (#34, ERD §4.6 [W10]).
 *
 * <p>{@code GET /albums} 목록 응답이 artist/genre/label 을 페치 조인 없이 lazy 로 끌어와
 * Hibernate 가 album 1건 + (artist + genre + label) per-row 의 별도 SELECT 를 발행함을 고정한다.
 * W10 에서 fetch join 또는 EntityGraph 로 개선하기 전까지 본 테스트가 시연 자료의 보존을
 * 보장한다 — 누군가 "성능 버그" 로 오해해 fetch join 을 추가하면 이 테스트가 즉시 실패한다.
 *
 * <p>판정 기준: 5건 조회 시 쿼리 수 > 5 (count + albums 본 쿼리 + lazy proxy resolve N×3).
 * 본 게이트는 정확한 수치를 고정하지 않고 "1쿼리가 아니다" 를 보장한다 — Hibernate 버전 변화에
 * 따른 미세한 쿼리 수 차이로 깨지는 것을 막기 위함.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("AlbumService.search — 의도적 N+1 보존 (W10 시연)")
class AlbumQueryN1Test {

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

    @BeforeEach
    void setUp() {
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();

        // 각 album 이 unique artist/genre/label 을 참조하도록 — 1차 캐시 hit 으로 N+1 이 가려지는
        // 것을 막아 시연 자료의 정확성을 보장한다.
        for (int i = 0; i < 5; i++) {
            Artist artist = artistRepository.saveAndFlush(Artist.create("Artist " + i, null));
            Genre genre = genreRepository.saveAndFlush(Genre.create("Genre " + i));
            Label label = labelRepository.saveAndFlush(Label.create("Label " + i));
            albumRepository.saveAndFlush(Album.create(
                    "Album " + i, artist, genre, label,
                    (short) (1965 + i), AlbumFormat.LP_12, 30000L, 5,
                    AlbumStatus.SELLING, false, null, null));
        }

        // Persistence Context 초기화 — 이전 saveAndFlush 가 채워둔 1차 캐시로 인해 N+1 이
        // 가려지는 것을 막는다. 서비스 호출은 항상 비어있는 세션에서 시작해야 시연 자료가 정확.
        entityManager.clear();

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @Test
    @DisplayName("5건 SELLING 검색 시 쿼리 수 > 5 — fetch join 미적용으로 lazy proxy 가 행마다 풀림")
    void search_triggersN1Selects_byDesign() {
        AlbumSearchCondition cond = new AlbumSearchCondition(
                null, null, null, null, null, null, null, null, null, AlbumStatus.SELLING);

        var page = albumService.search(cond, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(5);

        // unique 연관 5×3 = 15 lazy load + albums 본 쿼리 1 + (단일 페이지라 count 스킵될 수 있음)
        // → 최소 15 이상 예상. 정확한 수치는 Hibernate 버전에 따라 달라질 수 있어 5 (행 수) 이상만 게이트.
        long queryCount = statistics.getPrepareStatementCount();
        assertThat(queryCount)
                .as("페치 조인을 추가하면 이 어서션이 깨진다 — W10 시연 자료 보호 (행수=%d, 쿼리수=%d)", 5, queryCount)
                .isGreaterThan(5);
    }
}
