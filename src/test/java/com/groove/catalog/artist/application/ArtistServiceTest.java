package com.groove.catalog.artist.application;

import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.artist.exception.ArtistInUseException;
import com.groove.catalog.artist.exception.ArtistNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArtistService 단위 테스트")
class ArtistServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    private ArtistService artistService;

    @BeforeEach
    void setUp() {
        artistService = new ArtistService(artistRepository, albumRepository);
    }

    @Test
    @DisplayName("create → 중복 검사 없이 곧바로 save (동명이인 허용)")
    void create_savesWithoutDuplicateCheck() {
        Artist persisted = Artist.create("The Beatles", "British rock band");
        given(artistRepository.save(any(Artist.class))).willReturn(persisted);

        Artist result = artistService.create(new ArtistCommand("The Beatles", "British rock band"));

        assertThat(result.getName()).isEqualTo("The Beatles");
        assertThat(result.getDescription()).isEqualTo("British rock band");
        then(artistRepository).should().save(any(Artist.class));
    }

    @Test
    @DisplayName("create → description 이 null 이어도 정상 저장")
    void create_acceptsNullDescription() {
        Artist persisted = Artist.create("Anonymous", null);
        given(artistRepository.save(any(Artist.class))).willReturn(persisted);

        Artist result = artistService.create(new ArtistCommand("Anonymous", null));

        assertThat(result.getDescription()).isNull();
    }

    @Test
    @DisplayName("update → 존재하지 않는 id 면 404")
    void update_throwsWhenIdMissing() {
        given(artistRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> artistService.update(99L, new ArtistCommand("X", null)))
                .isInstanceOf(ArtistNotFoundException.class);
    }

    @Test
    @DisplayName("update → 이름과 설명을 동시에 갱신 (PUT 전체 교체)")
    void update_replacesNameAndDescription() {
        Artist existing = Artist.create("Old Name", "Old desc");
        given(artistRepository.findById(1L)).willReturn(Optional.of(existing));

        Artist result = artistService.update(1L, new ArtistCommand("New Name", "New desc"));

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("New desc");
    }

    @Test
    @DisplayName("update → description 을 null 로 보내면 명시적 지움")
    void update_clearsDescriptionWhenNull() {
        Artist existing = Artist.create("Name", "Old desc");
        given(artistRepository.findById(1L)).willReturn(Optional.of(existing));

        Artist result = artistService.update(1L, new ArtistCommand("Name", null));

        assertThat(result.getDescription()).isNull();
    }

    @Test
    @DisplayName("delete → 존재하지 않는 id 면 404")
    void delete_throwsWhenIdMissing() {
        given(artistRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> artistService.delete(99L))
                .isInstanceOf(ArtistNotFoundException.class);
        then(artistRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("delete → 존재하고 album 미참조면 entity 로 delete 호출 후 flush")
    void delete_callsDeleteEntity() {
        Artist existing = Artist.create("X", null);
        given(artistRepository.findById(1L)).willReturn(Optional.of(existing));
        given(albumRepository.existsByArtist_Id(1L)).willReturn(false);

        artistService.delete(1L);

        then(artistRepository).should().delete(existing);
        then(artistRepository).should().flush();
    }

    @Test
    @DisplayName("delete → album 이 참조 중이면 409 ArtistInUse")
    void delete_throwsWhenAlbumReferences() {
        Artist existing = Artist.create("X", null);
        given(artistRepository.findById(1L)).willReturn(Optional.of(existing));
        given(albumRepository.existsByArtist_Id(1L)).willReturn(true);

        assertThatThrownBy(() -> artistService.delete(1L))
                .isInstanceOf(ArtistInUseException.class);
        then(artistRepository).should(never()).delete(any(Artist.class));
    }

    @Test
    @DisplayName("delete → 사전검사 통과 후 동시 INSERT race → DataIntegrityViolation 을 ArtistInUse 로 변환")
    void delete_translatesIntegrityViolationToInUse() {
        Artist existing = Artist.create("X", null);
        given(artistRepository.findById(1L)).willReturn(Optional.of(existing));
        given(albumRepository.existsByArtist_Id(1L)).willReturn(false);
        willThrow(new DataIntegrityViolationException("fk_album_artist"))
                .given(artistRepository).flush();

        assertThatThrownBy(() -> artistService.delete(1L))
                .isInstanceOf(ArtistInUseException.class);
    }

    @Test
    @DisplayName("findAll → 호출자가 전달한 Pageable 그대로 위임")
    void findAll_delegatesPageable() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));
        Page<Artist> page = new PageImpl<>(List.of(Artist.create("A", null)), pageable, 1);
        given(artistRepository.findAll(pageable)).willReturn(page);

        Page<Artist> result = artistService.findAll(pageable);

        assertThat(result.getContent()).hasSize(1);
        then(artistRepository).should().findAll(pageable);
    }

    @Test
    @DisplayName("findById → 존재하지 않는 id 면 404")
    void findById_throwsWhenMissing() {
        given(artistRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> artistService.findById(99L))
                .isInstanceOf(ArtistNotFoundException.class);
    }
}
