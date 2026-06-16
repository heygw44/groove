package com.groove.catalog.album.domain;

import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.support.TestcontainersConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("AlbumRepository 통합 테스트 (Testcontainers MySQL)")
class AlbumRepositoryTest {

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private EntityManager em;

    private Artist artist;
    private Genre genre;
    private Label label;

    /**
     * FK 의존 순서 — album 을 먼저 비워야 부모(artist/genre/label) 의 deleteAllInBatch 가
     * ON DELETE RESTRICT 에 걸리지 않는다. @DataJpaTest 의 트랜잭션 롤백이 외부 행을 다시 살리지
     * 않으므로, 다른 @SpringBootTest 가 커밋한 잔여 행이 있어도 이 순서로 안전하게 정리된다.
     */
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
    @DisplayName("save → id 발급 + 감사 컬럼 채워짐 + label nullable")
    void save_persistsWithAuditAndNullableLabel() {
        Album saved = albumRepository.saveAndFlush(Album.create(
                "Abbey Road", artist, genre, null,
                (short) 1969, AlbumFormat.LP_12, 35000L, 8,
                AlbumStatus.SELLING, false, null, null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getLabel()).isNull();
    }

    @Test
    @DisplayName("existsByArtist_Id / existsByGenre_Id / existsByLabel_Id → FK 역참조 검사")
    void existsByForeignKey_returnsTrueWhenAlbumReferences() {
        albumRepository.saveAndFlush(Album.create(
                "Album", artist, genre, label,
                (short) 1969, AlbumFormat.LP_12, 1000L, 0,
                AlbumStatus.SELLING, false, null, null));

        assertThat(albumRepository.existsByArtist_Id(artist.getId())).isTrue();
        assertThat(albumRepository.existsByGenre_Id(genre.getId())).isTrue();
        assertThat(albumRepository.existsByLabel_Id(label.getId())).isTrue();

        assertThat(albumRepository.existsByArtist_Id(99_999L)).isFalse();
    }

    @Test
    @DisplayName("[#204] album 검색 인덱스가 V21 에서 도입됨 — FULLTEXT + 필터/정렬 복합 인덱스")
    void searchIndexes_areAdded() {
        // W10-2(#204): V6 헤더의 [W10] 의도적 누락 인덱스를 V21 에서 도입했다. 풀스캔 Before/After 시연 완료.
        // 이 가드가 깨지면(인덱스 누락) 검색 성능 개선이 회귀했다는 신호다 — #203 의 N+1 가드 전환과 동일 의도.
        @SuppressWarnings("unchecked")
        List<String> indexNames = (List<String>) em.createNativeQuery(
                "SELECT INDEX_NAME FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'album' " +
                        "GROUP BY INDEX_NAME")
                .getResultList();

        assertThat(indexNames).contains(
                "PRIMARY",
                "idx_album_artist",
                "idx_album_genre",
                "idx_album_label",
                // V21 도입 (#204)
                "ft_album_keyword",
                "idx_album_status_created",
                "idx_album_search",
                "idx_album_year",
                "idx_album_limited",
                // V25 도입 (#244) — price/release_year 정렬 keyset 의 filesort 제거
                "idx_album_status_price",
                "idx_album_status_year"
        );
        // 키워드는 단일 B-Tree(idx_album_title)가 아니라 FULLTEXT 로 해소했으므로 plain title 인덱스는 두지 않는다.
        assertThat(indexNames).doesNotContain("idx_album_title");
    }

    @Test
    @DisplayName("[#204] updateArtistNameByArtistId → 해당 artist 의 모든 album.artist_name 일괄 갱신")
    void updateArtistNameByArtistId_syncsDenormalizedColumn() {
        Album a1 = albumRepository.saveAndFlush(Album.create(
                "T1", artist, genre, null, (short) 2020, AlbumFormat.LP_12, 1000L, 1,
                AlbumStatus.SELLING, false, null, null));
        Album a2 = albumRepository.saveAndFlush(Album.create(
                "T2", artist, genre, null, (short) 2021, AlbumFormat.LP_12, 1000L, 1,
                AlbumStatus.SELLING, false, null, null));
        // create 시 비정규화 컬럼이 artist.name 으로 채워진다.
        assertThat(a1.getArtistName()).isEqualTo("The Beatles");

        int updated = albumRepository.updateArtistNameByArtistId(artist.getId(), "Renamed");
        em.clear(); // 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로 재조회 위해 비운다.

        assertThat(updated).isEqualTo(2);
        assertThat(albumRepository.findById(a1.getId()).orElseThrow().getArtistName()).isEqualTo("Renamed");
        assertThat(albumRepository.findById(a2.getId()).orElseThrow().getArtistName()).isEqualTo("Renamed");
    }
}
