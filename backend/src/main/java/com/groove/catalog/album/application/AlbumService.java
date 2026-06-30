package com.groove.catalog.album.application;

import com.groove.catalog.album.api.dto.AlbumDetailResponse;
import com.groove.catalog.album.api.dto.AlbumSummaryResponse;
import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumSpecs;
import com.groove.catalog.album.exception.AlbumInUseException;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.artist.exception.ArtistNotFoundException;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.genre.exception.GenreNotFoundException;
import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.catalog.label.exception.LabelNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 앨범 CRUD + 재고 조정 트랜잭션 경계. FK(artist/genre/label) 는 findById 로 로드하고 없으면
 * 도메인별 NotFound. label 은 nullable — id 가 null 이면 검증 생략. 반환 엔티티는
 * initializeAssociations 로 lazy 프록시를 트랜잭션 내에서 초기화한다.
 */
@Service
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final GenreRepository genreRepository;
    private final LabelRepository labelRepository;
    private final AlbumRatingProvider albumRatingProvider;
    private final List<AlbumReferenceGuard> referenceGuards;

    public AlbumService(AlbumRepository albumRepository,
                        ArtistRepository artistRepository,
                        GenreRepository genreRepository,
                        LabelRepository labelRepository,
                        AlbumRatingProvider albumRatingProvider,
                        List<AlbumReferenceGuard> referenceGuards) {
        this.albumRepository = albumRepository;
        this.artistRepository = artistRepository;
        this.genreRepository = genreRepository;
        this.labelRepository = labelRepository;
        this.albumRatingProvider = albumRatingProvider;
        this.referenceGuards = referenceGuards;
    }

    @CacheEvict(cacheNames = AlbumCaches.LANDING_LIST, allEntries = true)
    @Transactional
    public Album create(AlbumCommand command, int initialStock) {
        Artist artist = loadArtist(command.artistId());
        Genre genre = loadGenre(command.genreId());
        Label label = loadLabel(command.labelId());

        Album album = Album.create(
                command.title(),
                artist,
                genre,
                label,
                command.releaseYear(),
                command.format(),
                command.price(),
                initialStock,
                command.status(),
                command.limited(),
                command.coverImageUrl(),
                command.description()
        );
        return initializeAssociations(albumRepository.save(album));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = AlbumCaches.DETAIL, key = "#id"),
            @CacheEvict(cacheNames = AlbumCaches.LANDING_LIST, allEntries = true)
    })
    @Transactional
    public Album update(Long id, AlbumCommand command) {
        Album album = albumRepository.findById(id)
                .orElseThrow(AlbumNotFoundException::new);

        Artist artist = loadArtist(command.artistId());
        Genre genre = loadGenre(command.genreId());
        Label label = loadLabel(command.labelId());

        album.update(
                command.title(),
                artist,
                genre,
                label,
                command.releaseYear(),
                command.format(),
                command.price(),
                command.status(),
                command.limited(),
                command.coverImageUrl(),
                command.description()
        );
        return initializeAssociations(album);
    }

    /** 관리자 단건 재고조정 (delta 음수 가능). album 행을 비관락(SELECT … FOR UPDATE)으로 잠근다. */
    @Caching(evict = {
            @CacheEvict(cacheNames = AlbumCaches.DETAIL, key = "#id"),
            @CacheEvict(cacheNames = AlbumCaches.LANDING_LIST, allEntries = true)
    })
    @Transactional
    public Album adjustStock(Long id, int delta) {
        Album album = albumRepository.findByIdForUpdate(id)
                .orElseThrow(AlbumNotFoundException::new);
        album.adjustStock(delta);
        return initializeAssociations(album);
    }

    /** 앨범 삭제. 이 메서드가 트랜잭션 경계 소유자다 (비트랜잭션 컨트롤러에서 직접 호출). */
    @Caching(evict = {
            @CacheEvict(cacheNames = AlbumCaches.DETAIL, key = "#id"),
            @CacheEvict(cacheNames = AlbumCaches.LANDING_LIST, allEntries = true)
    })
    @Transactional
    public void delete(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(AlbumNotFoundException::new);
        // cart_item/order_item 이 참조 중이면 409 로 거절. review 는 ON DELETE CASCADE.
        // 사전 검사~flush 사이 TOCTOU 경합 창이 있으나, cart_item/order_item 이 ON DELETE RESTRICT 라
        // 그 사이 새 참조가 생기면 flush 가 FK 위반→DataIntegrityViolationException→AlbumInUseException(409) 으로
        // 안전 변환한다. 이 안전망이 견고하므로 SELECT FOR UPDATE 행 락은 두지 않는다.
        if (referenceGuards.stream().anyMatch(guard -> guard.isReferenced(id))) {
            throw new AlbumInUseException();
        }
        try {
            albumRepository.delete(album);
            albumRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new AlbumInUseException(ex);
        }
    }

    // artist/genre/label 은 findAll 의 @EntityGraph 로 동반 페치한다.
    // 공개 기본 랜딩(필터 전무 + 기본 첫 페이지)만 단일 엔트리로 캐시. sync=true 는 이 노드 로컬의 동시 미스만 단일 로딩으로 합친다(Redis 분산 single-flight 아님 — #366).
    @Cacheable(cacheNames = AlbumCaches.LANDING_LIST, key = AlbumCaches.LANDING_KEY,
            condition = AlbumCaches.LANDING_CONDITION, sync = true)
    @Transactional(readOnly = true)
    public Page<AlbumSummaryResponse> search(AlbumSearchCondition condition, Pageable pageable) {
        Page<Album> page = albumRepository.findAll(buildSpec(condition), pageable);
        Map<Long, AlbumRating> ratings = albumRatingProvider.ratingsByAlbumId(page.getContent().stream().map(Album::getId).toList());
        return page.map(album -> AlbumSummaryResponse.from(album, ratings.getOrDefault(album.getId(), AlbumRating.NONE)));
    }

    // keyset 변형. fluent findBy 경로는 @EntityGraph 미적용이라 to-one 연관(artist/genre/label)은
    // 각 대상 엔티티 클래스 레벨 @BatchSize 로 IN 쿼리 일괄 페치된다.
    @Transactional(readOnly = true)
    public Window<AlbumSummaryResponse> searchKeyset(AlbumSearchCondition condition, int size, Sort sort, ScrollPosition position) {
        Specification<Album> spec = buildSpec(condition);
        Window<Album> window = albumRepository.findBy(spec, query -> query.sortBy(sort).limit(size).scroll(position));
        Map<Long, AlbumRating> ratings = albumRatingProvider.ratingsByAlbumId(window.getContent().stream().map(Album::getId).toList());
        return window.map(album -> AlbumSummaryResponse.from(album, ratings.getOrDefault(album.getId(), AlbumRating.NONE)));
    }

    /** 공개 검색 동적 조건 조립 — offset(search)·keyset(searchKeyset) 양 경로 공용. */
    private Specification<Album> buildSpec(AlbumSearchCondition condition) {
        return Specification.allOf(
                AlbumSpecs.keyword(condition.keyword()),
                AlbumSpecs.hasArtistId(condition.artistId()),
                AlbumSpecs.hasGenreId(condition.genreId()),
                AlbumSpecs.hasLabelId(condition.labelId()),
                AlbumSpecs.priceBetween(condition.minPrice(), condition.maxPrice()),
                AlbumSpecs.yearBetween(condition.minYear(), condition.maxYear()),
                AlbumSpecs.hasFormat(condition.format()),
                AlbumSpecs.isLimited(condition.limited()),
                AlbumSpecs.hasStatus(condition.status())
        );
    }

    // 조회는 status 무관하게 허용. 상세는 id 별로 캐시. sync=true 는 이 노드 로컬의 동일 id 동시 미스만 단일 로딩으로 합친다(Redis 분산 single-flight 아님 — #366).
    @Cacheable(cacheNames = AlbumCaches.DETAIL, key = "#id", sync = true)
    @Transactional(readOnly = true)
    public AlbumDetailResponse findDetail(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(AlbumNotFoundException::new);
        AlbumRating rating = albumRatingProvider.ratingsByAlbumId(List.of(id)).getOrDefault(id, AlbumRating.NONE);
        return AlbumDetailResponse.from(initializeAssociations(album), rating);
    }

    private Artist loadArtist(Long artistId) {
        return artistRepository.findById(artistId)
                .orElseThrow(ArtistNotFoundException::new);
    }

    private Genre loadGenre(Long genreId) {
        return genreRepository.findById(genreId)
                .orElseThrow(GenreNotFoundException::new);
    }

    private Label loadLabel(Long labelId) {
        if (labelId == null) {
            return null;
        }
        return labelRepository.findById(labelId)
                .orElseThrow(LabelNotFoundException::new);
    }

    /** artist/genre/label lazy 프록시를 트랜잭션 내에서 강제 초기화. */
    private Album initializeAssociations(Album album) {
        album.getArtist().getName();
        album.getGenre().getName();
        if (album.getLabel() != null) {
            album.getLabel().getName();
        }
        return album;
    }
}
