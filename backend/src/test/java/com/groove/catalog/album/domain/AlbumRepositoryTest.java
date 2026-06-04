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
    @DisplayName("[W10 보존] album 검색 인덱스가 의도적으로 누락되어 있음 — FK 인덱스만 존재")
    void searchIndexes_areIntentionallyMissing() {
        // ERD §4.6 [W10] 슬로우 쿼리 측정 시연 보존: search/year/title/limited 인덱스는 V6 에서 생성하지 않는다.
        // PR 리뷰가 "버그"로 오해하는 것을 막기 위해 본 테스트로 의도를 코드에 고정한다.
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
                "idx_album_label"
        );
        assertThat(indexNames).doesNotContain(
                "idx_album_search",
                "idx_album_year",
                "idx_album_title",
                "idx_album_limited"
        );
    }
}
