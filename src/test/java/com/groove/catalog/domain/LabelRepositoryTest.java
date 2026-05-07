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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("LabelRepository 통합 테스트 (Testcontainers MySQL)")
class LabelRepositoryTest {

    @Autowired
    private LabelRepository labelRepository;

    /**
     * 다른 통합 테스트(@SpringBootTest)가 커밋한 잔여 행을 제거하고 시작한다.
     * 본 클래스의 @DataJpaTest 는 트랜잭션 자동 롤백이라 외부에 영향을 주지 않는다.
     */
    @BeforeEach
    void cleanup() {
        labelRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("save → id + 감사 컬럼 자동 채워짐")
    void save_assignsIdAndAuditTimestamps() {
        Label saved = labelRepository.saveAndFlush(Label.create("Apple Records"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Apple Records");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("existsByName / existsByNameAndIdNot / findByName 동작")
    void derivedQueries() {
        Label apple = labelRepository.saveAndFlush(Label.create("Apple Records"));
        Label motown = labelRepository.saveAndFlush(Label.create("Motown"));

        assertThat(labelRepository.existsByName("Apple Records")).isTrue();
        assertThat(labelRepository.existsByName("Unknown")).isFalse();

        assertThat(labelRepository.existsByNameAndIdNot("Apple Records", apple.getId())).isFalse();
        assertThat(labelRepository.existsByNameAndIdNot("Apple Records", motown.getId())).isTrue();

        Optional<Label> found = labelRepository.findByName("Motown");
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("동일 name 두 번 저장 → DB UNIQUE 제약 위반")
    void uniqueNameConstraint_violationThrows() {
        labelRepository.saveAndFlush(Label.create("Blue Note"));

        assertThatThrownBy(() -> labelRepository.saveAndFlush(Label.create("Blue Note")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
