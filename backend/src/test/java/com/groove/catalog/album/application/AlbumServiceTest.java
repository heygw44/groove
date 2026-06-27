package com.groove.catalog.album.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.album.exception.AlbumInUseException;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.catalog.album.exception.IllegalStockAdjustmentException;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.artist.exception.ArtistNotFoundException;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.genre.exception.GenreNotFoundException;
import com.groove.catalog.album.api.dto.AlbumDetailResponse;
import com.groove.catalog.album.api.dto.AlbumSummaryResponse;
import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.catalog.label.exception.LabelNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
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
    @Mock
    private AlbumRatingProvider albumRatingProvider;
    // 앨범 참조 가드(order·cart 도메인이 구현) — 단위 테스트에선 목으로 주입(#349).
    @Mock
    private AlbumReferenceGuard cartGuard;
    @Mock
    private AlbumReferenceGuard orderGuard;

    private AlbumService albumService;

    @BeforeEach
    void setUp() {
        albumService = new AlbumService(albumRepository, artistRepository, genreRepository, labelRepository,
                albumRatingProvider, List.of(cartGuard, orderGuard));
    }

    private Album sampleAlbum() {
        return Album.create("X", Artist.create("A", null), Genre.create("G"), null,
                (short) 1990, AlbumFormat.EP, 0L, 0,
                AlbumStatus.SELLING, false, null, null);
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
        given(albumRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.adjustStock(1L, 1))
                .isInstanceOf(AlbumNotFoundException.class);
    }

    @Test
    @DisplayName("adjustStock → 결과 음수면 400 IllegalStockAdjustment")
    void adjustStock_throwsWhenResultNegative() {
        Album existing = Album.create("X", Artist.create("A", null), Genre.create("G"), null,
                (short) 1990, AlbumFormat.EP, 0L, 1,
                AlbumStatus.SELLING, false, null, null);
        given(albumRepository.findByIdForUpdate(1L)).willReturn(Optional.of(existing));

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
        given(albumRepository.findByIdForUpdate(1L)).willReturn(Optional.of(existing));

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
    @DisplayName("delete → 존재하고 참조 가드 모두 false 면 entity 로 delete 호출")
    void delete_callsDeleteEntity() {
        Album existing = sampleAlbum();
        given(albumRepository.findById(1L)).willReturn(Optional.of(existing));

        albumService.delete(1L);

        then(cartGuard).should().isReferenced(1L);
        then(orderGuard).should().isReferenced(1L);
        then(albumRepository).should().delete(existing);
        then(albumRepository).should().flush();
    }

    @Test
    @DisplayName("delete → cart 참조 가드가 true 면 409 AlbumInUse (delete 호출 안 함)")
    void delete_throwsWhenReferencedByCart() {
        Album existing = sampleAlbum();
        given(albumRepository.findById(1L)).willReturn(Optional.of(existing));
        given(cartGuard.isReferenced(1L)).willReturn(true);

        assertThatThrownBy(() -> albumService.delete(1L))
                .isInstanceOf(AlbumInUseException.class);
        then(albumRepository).should(never()).delete(any(Album.class));
    }

    @Test
    @DisplayName("delete → order 참조 가드가 true 면 409 AlbumInUse (delete 호출 안 함)")
    void delete_throwsWhenReferencedByOrder() {
        Album existing = sampleAlbum();
        given(albumRepository.findById(1L)).willReturn(Optional.of(existing));
        given(orderGuard.isReferenced(1L)).willReturn(true);

        assertThatThrownBy(() -> albumService.delete(1L))
                .isInstanceOf(AlbumInUseException.class);
        then(albumRepository).should(never()).delete(any(Album.class));
    }

    @Test
    @DisplayName("delete → 사전검사 통과 후 동시 INSERT race → DataIntegrityViolation 을 AlbumInUse 로 변환")
    void delete_translatesIntegrityViolationToInUse() {
        Album existing = sampleAlbum();
        given(albumRepository.findById(1L)).willReturn(Optional.of(existing));
        willThrow(new DataIntegrityViolationException("fk_cart_item_album"))
                .given(albumRepository).flush();

        assertThatThrownBy(() -> albumService.delete(1L))
                .isInstanceOf(AlbumInUseException.class);
    }

    @Test
    @DisplayName("adjustStock → 결과가 Integer.MAX_VALUE 초과 → 400 (int 오버플로 방지)")
    void adjustStock_throwsWhenResultOverflowsInt() {
        Album existing = Album.create("X", Artist.create("A", null), Genre.create("G"), null,
                (short) 1990, AlbumFormat.EP, 0L, 1_000_000,
                AlbumStatus.SELLING, false, null, null);
        given(albumRepository.findByIdForUpdate(1L)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> albumService.adjustStock(1L, Integer.MAX_VALUE))
                .isInstanceOf(IllegalStockAdjustmentException.class);
        assertThat(existing.getStock()).isEqualTo(1_000_000);
    }

    private Album albumWithAssociations() {
        return Album.create("Abbey Road", Artist.create("The Beatles", "desc"), Genre.create("Rock"),
                Label.create("Apple"), (short) 1969, AlbumFormat.LP_12, 35000L, 8,
                AlbumStatus.SELLING, false, "https://img", "desc");
    }

    @Test
    @DisplayName("search → 결과가 비면 평점 provider 가 빈 맵을 돌려 빈 페이지를 그대로 반환")
    void search_emptyResult_returnsEmpty() {
        AlbumSearchCondition cond = new AlbumSearchCondition(
                null, null, null, null, null, null, null, null, null, null, AlbumStatus.SELLING);
        given(albumRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .willReturn(Page.<Album>empty());
        given(albumRatingProvider.ratingsByAlbumId(List.of())).willReturn(Map.of());

        Page<AlbumSummaryResponse> page = albumService.search(cond, PageRequest.of(0, 20));

        assertThat(page).isEmpty();
    }

    @Test
    @DisplayName("findDetail → 리뷰가 있으면 averageRating(소수1자리 반올림)·reviewCount 를 응답에 채운다")
    void findDetail_fillsRatingFromAggregate() {
        Album album = albumWithAssociations();
        given(albumRepository.findById(1L)).willReturn(Optional.of(album));
        given(albumRatingProvider.ratingsByAlbumId(List.of(1L)))
                .willReturn(Map.of(1L, AlbumRating.of(4.75, 4L)));

        AlbumDetailResponse response = albumService.findDetail(1L);

        assertThat(response.averageRating()).isEqualTo(4.8);
        assertThat(response.reviewCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("findDetail → 리뷰가 없으면 averageRating=null, reviewCount=0")
    void findDetail_noReviews_returnsNullRating() {
        Album album = albumWithAssociations();
        given(albumRepository.findById(1L)).willReturn(Optional.of(album));
        given(albumRatingProvider.ratingsByAlbumId(List.of(1L))).willReturn(Map.of());

        AlbumDetailResponse response = albumService.findDetail(1L);

        assertThat(response.averageRating()).isNull();
        assertThat(response.reviewCount()).isZero();
    }
}
