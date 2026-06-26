package com.groove.catalog.album.application;

import com.groove.catalog.album.api.dto.AlbumDetailResponse;
import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.catalog.artist.application.ArtistCommand;
import com.groove.catalog.artist.application.ArtistService;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 카탈로그 조회 캐시 동작 가드.
// spring.cache.type=caffeine 으로 켜서 Caffeine 캐시 동작을 검증한다.
// ① findDetail 두 번째 호출이 캐시 서빙 ② admin 쓰기(update/adjustStock/delete)가 상세 캐시 즉시 evict
// ③ 공개 기본 랜딩 목록 캐시 + 등록 시 clear ④ 필터가 있는 검색은 랜딩 캐시를 우회.
@SpringBootTest(properties = "spring.cache.type=caffeine")
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("AlbumService — 카탈로그 조회 캐시 (#236)")
class AlbumCacheTest {

    // 컨트롤러 기본값과 동일한 공개 랜딩 요청
    private static final AlbumSearchCondition LANDING = new AlbumSearchCondition(
            null, null, null, null, null, null, null, null, null, null, AlbumStatus.SELLING);
    private static final Pageable LANDING_PAGE =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    // artist/genre/label 의 unique name 충돌을 피하기 위한 시드 시퀀스
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private AlbumService albumService;

    @Autowired
    private ArtistService artistService;

    @MockitoSpyBean
    private AlbumRepository albumRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        // 매 테스트 시작 시 캐시를 비운다
        cacheManager.getCacheNames()
                .forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
        clearInvocations(albumRepository);
    }

    @Test
    @DisplayName("findDetail — 두 번째 호출은 캐시 서빙(findById 1회만)")
    void findDetail_servesSecondCallFromCache() {
        Long id = seedSellingAlbum("Cached Detail", 5).getId();
        clearInvocations(albumRepository);

        AlbumDetailResponse first = albumService.findDetail(id);
        AlbumDetailResponse second = albumService.findDetail(id);

        assertThat(second).isEqualTo(first);
        // 2회 호출했지만 캐시 적중으로 본 쿼리(findById)는 1회만
        verify(albumRepository, times(1)).findById(id);
    }

    @Test
    @DisplayName("update — 상세 캐시 즉시 evict(변경값이 바로 보임)")
    void update_evictsDetailCache() {
        Album album = seedSellingAlbum("Before", 5);
        Long id = album.getId();

        albumService.findDetail(id); // 캐시 적재 (title=Before)
        albumService.update(id, commandFrom(album, "After"));

        assertThat(albumService.findDetail(id).title())
                .as("update 가 상세 캐시를 evict 해 변경된 title 이 즉시 보여야 한다")
                .isEqualTo("After");
    }

    @Test
    @DisplayName("adjustStock — 상세 캐시 즉시 evict(재고 변경이 바로 보임)")
    void adjustStock_evictsDetailCache() {
        Long id = seedSellingAlbum("Stock Evict", 5).getId();

        assertThat(albumService.findDetail(id).stock()).isEqualTo(5); // 캐시 적재
        albumService.adjustStock(id, 10);

        assertThat(albumService.findDetail(id).stock())
                .as("adjustStock 가 상세 캐시를 evict 해 갱신된 stock 이 즉시 보여야 한다")
                .isEqualTo(15);
    }

    @Test
    @DisplayName("delete — 상세 캐시 즉시 evict(이후 조회는 404)")
    void delete_evictsDetailCache() {
        Long id = seedSellingAlbum("To Delete", 5).getId();

        albumService.findDetail(id); // 캐시 적재
        albumService.delete(id);

        assertThatThrownBy(() -> albumService.findDetail(id))
                .as("delete 후에는 캐시가 비워져 재조회가 404 여야 한다(stale 캐시 서빙 금지)")
                .isInstanceOf(AlbumNotFoundException.class);
    }

    @Test
    @DisplayName("아티스트 이름변경 — 상세 캐시 evict(변경된 아티스트 이름이 바로 보임)")
    void artistRename_evictsAlbumDetailCache() {
        Album album = seedSellingAlbum("Artist Rename", 5);
        Long albumId = album.getId();
        Long artistId = album.getArtist().getId();

        String original = albumService.findDetail(albumId).artist().name(); // 캐시 적재
        artistService.update(artistId, new ArtistCommand("Renamed Artist", null));

        assertThat(albumService.findDetail(albumId).artist().name())
                .as("아티스트 이름변경이 상세 캐시를 evict 해 새 이름이 즉시 보여야 한다")
                .isNotEqualTo(original)
                .isEqualTo("Renamed Artist");
    }

    @Test
    @DisplayName("공개 랜딩 목록 — 두 번째 호출은 캐시 서빙(findAll 1회만)")
    void landingList_servesSecondCallFromCache() {
        seedSellingAlbum("Landing A", 5);
        clearInvocations(albumRepository);

        albumService.search(LANDING, LANDING_PAGE);
        albumService.search(LANDING, LANDING_PAGE);

        verify(albumRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("등록 — 랜딩 목록 캐시 clear(새 앨범 반영, 상대 카운트)")
    void create_evictsLandingList() {
        Album seed = seedSellingAlbum("Landing Seed", 5);

        long before = albumService.search(LANDING, LANDING_PAGE).getTotalElements(); // 캐시 적재
        albumService.create(commandFrom(seed, "Landing New"), 3);
        long after = albumService.search(LANDING, LANDING_PAGE).getTotalElements();

        assertThat(after)
                .as("등록이 랜딩 캐시를 비워 총건수가 정확히 1 증가해야 한다(미evict 면 캐시값 그대로)")
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("필터 검색 — 랜딩 캐시 우회(가드, findAll 매번 실행)")
    void filteredSearch_bypassesLandingCache() {
        Album album = seedSellingAlbum("Filtered", 5);
        AlbumSearchCondition filtered = LANDING.withArtistId(album.getArtist().getId());
        clearInvocations(albumRepository);

        albumService.search(filtered, LANDING_PAGE);
        albumService.search(filtered, LANDING_PAGE);

        // 필터가 있으면 랜딩 캐시 우회 → 매 호출 DB 조회
        verify(albumRepository, times(2)).findAll(any(Specification.class), any(Pageable.class));
    }

    // unique artist/genre/label 을 새로 만들고 그 위에 SELLING 앨범 1건을 적재한다.
    private Album seedSellingAlbum(String title, int stock) {
        int n = SEQ.incrementAndGet();
        Artist artist = artistRepository.saveAndFlush(Artist.create("Artist " + title + " " + n, null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Genre " + title + " " + n));
        Label label = labelRepository.saveAndFlush(Label.create("Label " + title + " " + n));
        return albumRepository.saveAndFlush(Album.create(
                title, artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, 30000L, stock,
                AlbumStatus.SELLING, false, null, null));
    }

    // 시드 앨범의 연관(artist/genre/label)을 재사용해 새 title 의 커맨드를 만든다.
    private AlbumCommand commandFrom(Album album, String title) {
        return new AlbumCommand(
                title,
                album.getArtist().getId(),
                album.getGenre().getId(),
                album.getLabel().getId(),
                (short) 2020,
                AlbumFormat.LP_12,
                30000L,
                AlbumStatus.SELLING,
                false,
                null,
                null);
    }
}
