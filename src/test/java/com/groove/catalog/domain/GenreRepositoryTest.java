package com.groove.catalog.domain;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("GenreRepository 통합 테스트 (Testcontainers MySQL)")
class GenreRepositoryTest {

    @Autowired
    private GenreRepository genreRepository;

    /**
     * 다른 통합 테스트(@SpringBootTest)가 커밋한 잔여 행을 제거하고 시작한다.
     * 본 클래스의 @DataJpaTest 는 트랜잭션 자동 롤백이라 외부에 영향을 주지 않는다.
     */
    @BeforeEach
    void cleanup() {
        genreRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("save → id 발급 + createdAt/updatedAt 자동 채워짐")
    void save_assignsIdAndAuditTimestamps() {
        Genre saved = genreRepository.saveAndFlush(Genre.create("Rock"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Rock");
        assertThat(saved.getCreatedAt()).isNotNull().isBefore(Instant.now().plusSeconds(1));
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("existsByName → 이름 일치 시 true")
    void existsByName_returnsTrueForExistingName() {
        genreRepository.saveAndFlush(Genre.create("Jazz"));

        assertThat(genreRepository.existsByName("Jazz")).isTrue();
        assertThat(genreRepository.existsByName("K-Pop")).isFalse();
    }

    @Test
    @DisplayName("existsByNameAndIdNot → 같은 이름·다른 id 만 true (자기 자신 갱신 허용)")
    void existsByNameAndIdNot_excludesSelf() {
        Genre rock = genreRepository.saveAndFlush(Genre.create("Rock"));
        Genre jazz = genreRepository.saveAndFlush(Genre.create("Jazz"));

        assertThat(genreRepository.existsByNameAndIdNot("Rock", rock.getId())).isFalse();
        assertThat(genreRepository.existsByNameAndIdNot("Rock", jazz.getId())).isTrue();
        assertThat(genreRepository.existsByNameAndIdNot("Pop", rock.getId())).isFalse();
    }

    @Test
    @DisplayName("findByName → 이름으로 단건 조회")
    void findByName_returnsSingle() {
        genreRepository.saveAndFlush(Genre.create("Hip-Hop"));

        Optional<Genre> found = genreRepository.findByName("Hip-Hop");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Hip-Hop");
        assertThat(genreRepository.findByName("Metal")).isEmpty();
    }

    @Test
    @DisplayName("동일 name 두 번 저장 → DB UNIQUE 제약 위반")
    void uniqueNameConstraint_violationThrows() {
        genreRepository.saveAndFlush(Genre.create("Rock"));

        assertThatThrownBy(() -> genreRepository.saveAndFlush(Genre.create("Rock")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rename → updatedAt 갱신, createdAt 유지")
    void rename_updatesTimestamp() {
        Genre saved = genreRepository.saveAndFlush(Genre.create("RnB"));
        Instant originalCreatedAt = saved.getCreatedAt();

        saved.rename("R&B");
        Genre updated = genreRepository.saveAndFlush(saved);

        assertThat(updated.getName()).isEqualTo("R&B");
        assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
    }
}
