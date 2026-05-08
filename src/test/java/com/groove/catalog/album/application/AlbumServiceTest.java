package com.groove.catalog.album.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.catalog.album.exception.IllegalStockAdjustmentException;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.artist.exception.ArtistNotFoundException;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.genre.exception.GenreNotFoundException;
import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.catalog.label.exception.LabelNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlbumService 단위 테스트")
class AlbumServiceTest {

    @Mock
    private AlbumRepository albumRepository;
    @Mock
    private ArtistRepository artistRepository;
    @Mock
    private GenreRepository genreRepository;
    @Mock
    private LabelRepository labelRepository;

    private AlbumService albumService;

    @BeforeEach
    void setUp() {
        albumService = new AlbumService(albumRepository, artistRepository, genreRepository, labelRepository);
    }

    private AlbumCommand baseCommand() {
        return new AlbumCommand("Abbey Road", 10L, 2L, 5L,
                (short) 1969, AlbumFormat.LP_12, 35000L,
                AlbumStatus.SELLING, false, null, null);
    }

    @Test
    @DisplayName("create → FK 모두 존재 시 album 영속, initialStock 으로 재고 초기화")
    void create_persistsWhenAllForeignKeysResolve() {
        Artist artist = Artist.create("The Beatles", null);
        Genre genre = Genre.create("Rock");
        Label label = Label.create("Apple");
        given(artistRepository.findById(10L)).willReturn(Optional.of(artist));
        given(genreRepository.findById(2L)).willReturn(Optional.of(genre));
        given(labelRepository.findById(5L)).willReturn(Optional.of(label));
        given(albumRepository.save(any(Album.class))).willAnswer(inv -> inv.getArgument(0));

        Album result = albumService.create(baseCommand(), 8);

        assertThat(result.getTitle()).isEqualTo("Abbey Road");
        assertThat(result.getArtist()).isSameAs(artist);
        assertThat(result.getGenre()).isSameAs(genre);
        assertThat(result.getLabel()).isSameAs(label);
        assertThat(result.getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("create → labelId 가 null 이면 label 조회 건너뛰고 album.label = null")
    void create_allowsNullLabelId() {
        Artist artist = Artist.create("The Beatles", null);
        Genre genre = Genre.create("Rock");
        given(artistRepository.findById(10L)).willReturn(Optional.of(artist));
        given(genreRepository.findById(2L)).willReturn(Optional.of(genre));
        given(albumRepository.save(any(Album.class))).willAnswer(inv -> inv.getArgument(0));

        AlbumCommand cmd = new AlbumCommand("X", 10L, 2L, null,
                (short) 1969, AlbumFormat.LP_12, 0L, AlbumStatus.SELLING, false, null, null);

        Album result = albumService.create(cmd, 0);

        assertThat(result.getLabel()).isNull();
        then(labelRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("create → artist 미존재 → 404 ArtistNotFound")
    void create_throwsWhenArtistMissing() {
        given(artistRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.create(baseCommand(), 0))
                .isInstanceOf(ArtistNotFoundException.class);
        then(albumRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("create → genre 미존재 → 404 GenreNotFound")
    void create_throwsWhenGenreMissing() {
        given(artistRepository.findById(10L)).willReturn(Optional.of(Artist.create("A", null)));
        given(genreRepository.findById(2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.create(baseCommand(), 0))
                .isInstanceOf(GenreNotFoundException.class);
        then(albumRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("create → label 미존재(non-null id) → 404 LabelNotFound")
    void create_throwsWhenLabelMissing() {
        given(artistRepository.findById(10L)).willReturn(Optional.of(Artist.create("A", null)));
        given(genreRepository.findById(2L)).willReturn(Optional.of(Genre.create("G")));
        given(labelRepository.findById(5L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.create(baseCommand(), 0))
                .isInstanceOf(LabelNotFoundException.class);
        then(albumRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("update → 미존재 album → 404 AlbumNotFound")
    void update_throwsWhenAlbumMissing() {
        given(albumRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.update(1L, baseCommand()))
                .isInstanceOf(AlbumNotFoundException.class);
    }

    @Test
    @DisplayName("update → 정상 흐름 시 stock 은 변하지 않고 나머지만 갱신")
    void update_doesNotMutateStock() {
        Artist oldArtist = Artist.create("Old A", null);
        Genre oldGenre = Genre.create("Old G");
        Album existing = Album.create("Old", oldArtist, oldGenre, null,
                (short) 1990, AlbumFormat.EP, 10000L, 5,
                AlbumStatus.SELLING, false, null, null);
        given(albumRepository.findById(1L)).willReturn(Optional.of(existing));

        Artist newArtist = Artist.create("New A", null);
        Genre newGenre = Genre.create("New G");
        Label newLabel = Label.create("New L");
        given(artistRepository.findById(10L)).willReturn(Optional.of(newArtist));
        given(genreRepository.findById(2L)).willReturn(Optional.of(newGenre));
        given(labelRepository.findById(5L)).willReturn(Optional.of(newLabel));

        AlbumCommand cmd = new AlbumCommand("New Title", 10L, 2L, 5L,
                (short) 2020, AlbumFormat.LP_DOUBLE, 50000L,
                AlbumStatus.HIDDEN, true, "https://img", "desc");

        Album result = albumService.update(1L, cmd);

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getArtist()).isSameAs(newArtist);
        assertThat(result.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("adjustStock → 미존재 album → 404")
    void adjustStock_throwsWhenAlbumMissing() {
        given(albumRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.adjustStock(1L, 1))
                .isInstanceOf(AlbumNotFoundException.class);
    }

    @Test
    @DisplayName("adjustStock → 결과 음수면 400 IllegalStockAdjustment")
    void adjustStock_throwsWhenResultNegative() {
        Album existing = Album.create("X", Artist.create("A", null), Genre.create("G"), null,
                (short) 1990, AlbumFormat.EP, 0L, 1,
                AlbumStatus.SELLING, false, null, null);
        given(albumRepository.findById(1L)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> albumService.adjustStock(1L, -2))
                .isInstanceOf(IllegalStockAdjustmentException.class);
        assertThat(existing.getStock()).isEqualTo(1);
    }

    @Test
    @DisplayName("adjustStock → 정상 적용 후 갱신된 album 반환")
    void adjustStock_appliesDelta() {
        Album existing = Album.create("X", Artist.create("A", null), Genre.create("G"), null,
                (short) 1990, AlbumFormat.EP, 0L, 5,
                AlbumStatus.SELLING, false, null, null);
        given(albumRepository.findById(1L)).willReturn(Optional.of(existing));

        Album result = albumService.adjustStock(1L, 3);

        assertThat(result.getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("delete → 미존재 → 404")
    void delete_throwsWhenAlbumMissing() {
        given(albumRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.delete(1L))
                .isInstanceOf(AlbumNotFoundException.class);
        then(albumRepository).should(never()).delete(any(Album.class));
    }

    @Test
    @DisplayName("delete → 존재하면 entity 로 delete 호출")
    void delete_callsDeleteEntity() {
        Album existing = Album.create("X", Artist.create("A", null), Genre.create("G"), null,
                (short) 1990, AlbumFormat.EP, 0L, 0,
                AlbumStatus.SELLING, false, null, null);
        given(albumRepository.findById(1L)).willReturn(Optional.of(existing));

        albumService.delete(1L);

        then(albumRepository).should().delete(existing);
    }

    @Test
    @DisplayName("adjustStock → 결과가 Integer.MAX_VALUE 초과 → 400 (int 오버플로 방지)")
    void adjustStock_throwsWhenResultOverflowsInt() {
        Album existing = Album.create("X", Artist.create("A", null), Genre.create("G"), null,
                (short) 1990, AlbumFormat.EP, 0L, 1_000_000,
                AlbumStatus.SELLING, false, null, null);
        given(albumRepository.findById(1L)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> albumService.adjustStock(1L, Integer.MAX_VALUE))
                .isInstanceOf(IllegalStockAdjustmentException.class);
        assertThat(existing.getStock()).isEqualTo(1_000_000);
    }
}
