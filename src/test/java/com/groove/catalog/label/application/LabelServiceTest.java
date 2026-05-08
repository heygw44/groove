package com.groove.catalog.label.application;

import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.catalog.label.exception.LabelNameDuplicatedException;
import com.groove.catalog.label.exception.LabelNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("LabelService 단위 테스트")
class LabelServiceTest {

    @Mock
    private LabelRepository labelRepository;

    private LabelService labelService;

    @BeforeEach
    void setUp() {
        labelService = new LabelService(labelRepository);
    }

    @Test
    @DisplayName("create → 중복 없으면 saveAndFlush")
    void create_persistsWhenNameUnique() {
        given(labelRepository.existsByName("Apple")).willReturn(false);
        Label persisted = Label.create("Apple");
        given(labelRepository.saveAndFlush(any(Label.class))).willReturn(persisted);

        Label result = labelService.create(new LabelCommand("Apple"));

        assertThat(result.getName()).isEqualTo("Apple");
    }

    @Test
    @DisplayName("create 선검사 중복 → 409")
    void create_throwsWhenPreCheckHitsDuplicate() {
        given(labelRepository.existsByName("Apple")).willReturn(true);

        assertThatThrownBy(() -> labelService.create(new LabelCommand("Apple")))
                .isInstanceOf(LabelNameDuplicatedException.class);
        then(labelRepository).should(never()).saveAndFlush(any(Label.class));
    }

    @Test
    @DisplayName("create 동시 INSERT race → DataIntegrityViolation 변환")
    void create_translatesIntegrityViolationToDomainException() {
        given(labelRepository.existsByName("Apple")).willReturn(false);
        given(labelRepository.saveAndFlush(any(Label.class)))
                .willThrow(new DataIntegrityViolationException("uk_label_name"));

        assertThatThrownBy(() -> labelService.create(new LabelCommand("Apple")))
                .isInstanceOf(LabelNameDuplicatedException.class);
    }

    @Test
    @DisplayName("update 미존재 id → 404")
    void update_throwsWhenIdMissing() {
        given(labelRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.update(99L, new LabelCommand("Anything")))
                .isInstanceOf(LabelNotFoundException.class);
    }

    @Test
    @DisplayName("update 다른 id 가 같은 name 사용 중 → 409")
    void update_throwsWhenAnotherIdHoldsSameName() {
        Label existing = Label.create("Motown");
        given(labelRepository.findById(1L)).willReturn(Optional.of(existing));
        given(labelRepository.existsByNameAndIdNot("Apple", 1L)).willReturn(true);

        assertThatThrownBy(() -> labelService.update(1L, new LabelCommand("Apple")))
                .isInstanceOf(LabelNameDuplicatedException.class);
    }

    @Test
    @DisplayName("update 자기 자신과 동일 name → 통과")
    void update_allowsSelfRenameToSameName() {
        Label existing = Label.create("Motown");
        given(labelRepository.findById(1L)).willReturn(Optional.of(existing));
        given(labelRepository.existsByNameAndIdNot("Motown", 1L)).willReturn(false);
        given(labelRepository.saveAndFlush(existing)).willReturn(existing);

        Label result = labelService.update(1L, new LabelCommand("Motown"));

        assertThat(result.getName()).isEqualTo("Motown");
    }

    @Test
    @DisplayName("delete 미존재 → 404")
    void delete_throwsWhenIdMissing() {
        given(labelRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.delete(99L))
                .isInstanceOf(LabelNotFoundException.class);
        then(labelRepository).should(never()).delete(any(Label.class));
    }

    @Test
    @DisplayName("delete 정상 → entity 로 delete 위임")
    void delete_callsDeleteEntity() {
        Label existing = Label.create("Apple Records");
        given(labelRepository.findById(1L)).willReturn(Optional.of(existing));

        labelService.delete(1L);

        then(labelRepository).should().delete(existing);
    }

    @Test
    @DisplayName("findAll → id ASC 정렬 위임")
    void findAll_delegatesWithIdAscSort() {
        given(labelRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).willReturn(List.of(Label.create("Apple")));

        List<Label> result = labelService.findAll();

        assertThat(result).hasSize(1);
        then(labelRepository).should().findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    @DisplayName("findById 미존재 → 404")
    void findById_throwsWhenMissing() {
        given(labelRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.findById(99L))
                .isInstanceOf(LabelNotFoundException.class);
    }
}
