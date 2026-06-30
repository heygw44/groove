package com.groove.catalog.album.application;

import com.groove.catalog.album.api.dto.AlbumDetailResponse;
import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 분산 Redis 캐시(spring.cache.type=redis) 직렬화 왕복 가드(#366). RedisCacheManager 는 put/get 마다 값을
// Redis 바이트로 직렬화/역직렬화하므로, 캐시 적중은 곧 JDK 직렬화 왕복이 성공했다는 증거다. 핵심은 랜딩의
// Page<AlbumSummaryResponse>(= PageImpl) — JSON default-typing 으론 역직렬화가 깨지지만 JDK 직렬화로는 안전함을 박는다.
@SpringBootTest(properties = "spring.cache.type=redis")
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("AlbumService — 분산 Redis 캐시 직렬화 왕복 (#366)")
class AlbumRedisCacheTest {

    private static final AlbumSearchCondition LANDING = new AlbumSearchCondition(
            null, null, null, null, null, null, null, null, null, null, AlbumStatus.SELLING);
    private static final Pageable LANDING_PAGE =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private AlbumService albumService;

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
        // 이 클래스가 쓰는 두 캐시만 비운다 — 공유(reuse) Redis 컨테이너라 flushDb 로 전부 지우면 다른 테스트까지 날린다.
        // RedisCache.clear() 는 캐시 이름 프리픽스 SCAN+DEL 이라 범위가 좁고 커넥션도 매니저가 닫는다.
        Objects.requireNonNull(cacheManager.getCache(AlbumCaches.DETAIL)).clear();
        Objects.requireNonNull(cacheManager.getCache(AlbumCaches.LANDING_LIST)).clear();
        clearInvocations(albumRepository);
    }

    @Test
    @DisplayName("상세 — Redis 왕복 후 두 번째 호출 캐시 서빙(findById 1회만)")
    void findDetail_roundTripsThroughRedis() {
        Long id = seedSellingAlbum("Redis Detail", 5).getId();
        clearInvocations(albumRepository);

        AlbumDetailResponse first = albumService.findDetail(id);   // 미스 → DB + Redis put(직렬화)
        AlbumDetailResponse second = albumService.findDetail(id);  // 적중 → Redis get(역직렬화)

        // 역직렬화된 값이 원본과 동일해야 한다(왕복 무손실)
        assertThat(second).isEqualTo(first);
        verify(albumRepository, times(1)).findById(id);
    }

    @Test
    @DisplayName("랜딩 목록 — Page(PageImpl) Redis 왕복(findAll 1회만)")
    void landingList_pageRoundTripsThroughRedis() {
        seedSellingAlbum("Redis Landing", 5);
        clearInvocations(albumRepository);

        Page<?> first = albumService.search(LANDING, LANDING_PAGE);   // 미스 → DB + Redis put(PageImpl 직렬화)
        Page<?> second = albumService.search(LANDING, LANDING_PAGE);  // 적중 → Redis get(PageImpl 역직렬화)

        // PageImpl 역직렬화가 깨지면 여기서 SerializationException 으로 실패한다(핵심 가드)
        assertThat(second.getTotalElements()).isEqualTo(first.getTotalElements());
        assertThat(second.getContent()).isEqualTo(first.getContent());
        verify(albumRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

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
}
