package com.groove.catalog.artist.domain;

import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("ArtistRepository 통합 테스트 (Testcontainers MySQL)")
class ArtistRepositoryTest {

    @Autowired
    private ArtistRepository artistRepository;

    @BeforeEach
    void cleanup() {
        artistRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("save → id 발급 + createdAt/updatedAt 자동 채워짐 + description nullable")
    void save_assignsIdAuditTimestampsAndAllowsNullDescription() {
        Artist saved = artistRepository.saveAndFlush(Artist.create("The Beatles", null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("The Beatles");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull().isBefore(Instant.now().plusSeconds(1));
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("동명이인 허용 → 동일 name 두 번 저장 시 둘 다 다른 id 로 저장됨 (UNIQUE 미적용)")
    void duplicateNameAllowed_namesakeIsPersistedSeparately() {
        Artist first = artistRepository.saveAndFlush(Artist.create("John", "Drummer"));
        Artist second = artistRepository.saveAndFlush(Artist.create("John", "Vocalist"));

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(artistRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("update → name/description 동시 갱신, updatedAt 갱신, createdAt 유지")
    void update_changesBothFieldsAndPreservesCreatedAt() {
        Artist saved = artistRepository.saveAndFlush(Artist.create("Old", "Old desc"));
        Instant originalCreatedAt = saved.getCreatedAt();
        Instant originalUpdatedAt = saved.getUpdatedAt();

        saved.update("New", "New desc");
        Artist updated = artistRepository.saveAndFlush(saved);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getDescription()).isEqualTo("New desc");
        assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(updated.getUpdatedAt()).isNotNull().isAfterOrEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName("findAll(Pageable) → page/size/sort 적용된 슬라이스 반환")
    void findAll_paginatesWithSort() {
        for (int i = 1; i <= 5; i++) {
            artistRepository.saveAndFlush(Artist.create("Artist " + i, null));
        }

        Page<Artist> firstPage = artistRepository.findAll(
                PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "id")));

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();

        Page<Artist> lastPage = artistRepository.findAll(
                PageRequest.of(2, 2, Sort.by(Sort.Direction.ASC, "id")));

        assertThat(lastPage.getContent()).hasSize(1);
        assertThat(lastPage.isLast()).isTrue();
    }
}
