package com.groove.catalog.genre.application;

import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.genre.exception.GenreInUseException;
import com.groove.catalog.genre.exception.GenreNameDuplicatedException;
import com.groove.catalog.genre.exception.GenreNotFoundException;
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
@DisplayName("GenreService 단위 테스트")
class GenreServiceTest {

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private AlbumRepository albumRepository;

    private GenreService genreService;

    @BeforeEach
    void setUp() {
        genreService = new GenreService(genreRepository, albumRepository);
    }

    @Test
    @DisplayName("create → 중복 없으면 saveAndFlush 호출 후 영속 객체 반환")
    void create_persistsWhenNameUnique() {
        given(genreRepository.existsByName("Rock")).willReturn(false);
        Genre persisted = Genre.create("Rock");
        given(genreRepository.saveAndFlush(any(Genre.class))).willReturn(persisted);

        Genre result = genreService.create(new GenreCommand("Rock"));

        assertThat(result.getName()).isEqualTo("Rock");
        then(genreRepository).should().saveAndFlush(any(Genre.class));
    }

    @Test
    @DisplayName("create → 선검사에서 중복 발견 시 409 (saveAndFlush 호출 안 함)")
    void create_throwsWhenPreCheckHitsDuplicate() {
        given(genreRepository.existsByName("Rock")).willReturn(true);

        assertThatThrownBy(() -> genreService.create(new GenreCommand("Rock")))
                .isInstanceOf(GenreNameDuplicatedException.class);
        then(genreRepository).should(never()).saveAndFlush(any(Genre.class));
    }

    @Test
    @DisplayName("create → 선검사 통과 후 동시 INSERT race → DataIntegrityViolation 을 도메인 예외로 변환")
    void create_translatesIntegrityViolationToDomainException() {
        given(genreRepository.existsByName("Rock")).willReturn(false);
        given(genreRepository.saveAndFlush(any(Genre.class)))
                .willThrow(new DataIntegrityViolationException("uk_genre_name"));

        assertThatThrownBy(() -> genreService.create(new GenreCommand("Rock")))
                .isInstanceOf(GenreNameDuplicatedException.class);
    }

    @Test
    @DisplayName("update → 존재하지 않는 id 면 404")
    void update_throwsWhenIdMissing() {
        given(genreRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> genreService.update(99L, new GenreCommand("Rock")))
                .isInstanceOf(GenreNotFoundException.class);
    }

    @Test
    @DisplayName("update → 다른 id 가 동일 name 사용 중이면 409")
    void update_throwsWhenAnotherIdHoldsSameName() {
        Genre existing = Genre.create("Jazz");
        given(genreRepository.findById(1L)).willReturn(Optional.of(existing));
        given(genreRepository.existsByNameAndIdNot("Rock", 1L)).willReturn(true);

        assertThatThrownBy(() -> genreService.update(1L, new GenreCommand("Rock")))
                .isInstanceOf(GenreNameDuplicatedException.class);
        then(genreRepository).should(never()).saveAndFlush(any(Genre.class));
    }

    @Test
    @DisplayName("update → 자기 자신과 동일 name 으로 갱신은 통과 (no-op rename)")
    void update_allowsSelfRenameToSameName() {
        Genre existing = Genre.create("Jazz");
        given(genreRepository.findById(1L)).willReturn(Optional.of(existing));
        given(genreRepository.existsByNameAndIdNot("Jazz", 1L)).willReturn(false);
        given(genreRepository.saveAndFlush(existing)).willReturn(existing);

        Genre result = genreService.update(1L, new GenreCommand("Jazz"));

        assertThat(result.getName()).isEqualTo("Jazz");
    }

    @Test
    @DisplayName("delete → 존재하지 않는 id 면 404")
    void delete_throwsWhenIdMissing() {
        given(genreRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> genreService.delete(99L))
                .isInstanceOf(GenreNotFoundException.class);
        then(genreRepository).should(never()).delete(any(Genre.class));
    }

    @Test
    @DisplayName("delete → 존재하고 album 미참조면 entity 로 delete 호출 후 flush")
    void delete_callsDeleteEntity() {
        Genre existing = Genre.create("Rock");
        given(genreRepository.findById(1L)).willReturn(Optional.of(existing));
        given(albumRepository.existsByGenre_Id(1L)).willReturn(false);

        genreService.delete(1L);

        then(genreRepository).should().delete(existing);
        then(genreRepository).should().flush();
    }

    @Test
    @DisplayName("delete → album 이 참조 중이면 409 GenreInUse (delete 호출 안 함)")
    void delete_throwsWhenAlbumReferences() {
        Genre existing = Genre.create("Rock");
        given(genreRepository.findById(1L)).willReturn(Optional.of(existing));
        given(albumRepository.existsByGenre_Id(1L)).willReturn(true);

        assertThatThrownBy(() -> genreService.delete(1L))
                .isInstanceOf(GenreInUseException.class);
        then(genreRepository).should(never()).delete(any(Genre.class));
    }

    @Test
    @DisplayName("delete → 사전검사 통과 후 동시 INSERT race → DataIntegrityViolation 을 GenreInUse 로 변환")
    void delete_translatesIntegrityViolationToInUse() {
        Genre existing = Genre.create("Rock");
        given(genreRepository.findById(1L)).willReturn(Optional.of(existing));
        given(albumRepository.existsByGenre_Id(1L)).willReturn(false);
        org.mockito.BDDMockito.willThrow(new DataIntegrityViolationException("fk_album_genre"))
                .given(genreRepository).flush();

        assertThatThrownBy(() -> genreService.delete(1L))
                .isInstanceOf(GenreInUseException.class);
    }

    @Test
    @DisplayName("findAll → id ASC 정렬로 위임")
    void findAll_delegatesWithIdAscSort() {
        given(genreRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).willReturn(List.of(Genre.create("Rock")));

        List<Genre> result = genreService.findAll();

        assertThat(result).hasSize(1);
        then(genreRepository).should().findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    @DisplayName("findById → 존재하지 않는 id 면 404")
    void findById_throwsWhenMissing() {
        given(genreRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> genreService.findById(99L))
                .isInstanceOf(GenreNotFoundException.class);
    }
}
