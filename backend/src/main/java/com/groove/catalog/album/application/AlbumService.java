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
import com.groove.cart.domain.CartRepository;
import com.groove.order.domain.OrderRepository;
import com.groove.review.domain.AlbumRatingView;
import com.groove.review.domain.ReviewRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 앨범 CRUD + 재고 조정 트랜잭션 경계.
 *
 * <p>FK 검증 정책: artist/genre/label 각각 {@code findById} 로 엔티티를 로드하고 없으면
 * 도메인별 NotFound (404) 를 던진다. label 은 nullable — id 가 null 이면 검증을 건너뛴다.
 *
 * <p>재고 조정({@link #adjustStock(Long, int)}) 은 비관적 락({@code SELECT ... FOR UPDATE}, #205 재사용)으로
 * 동시 admin 호출·주문 차감과 직렬화된다 — 갱신값을 응답에 그대로 써야 하고 음수/오버플로 가드를
 * {@code Album.adjustStock} 한 곳에 유지하려는 선택이다(#234). 주문 취소·환불·결제실패 보상·반품 재입고의
 * 복원 경로는 원자적 가산 UPDATE({@link com.groove.catalog.album.domain.StockRestorer})로 lost-update 를
 * 없앴다 — 비관 vs 낙관 vs 원자적 트레이드오프는 docs/improvements/concurrency.md §7.
 *
 * <p>전체 갱신({@link #update(Long, AlbumCommand)}) 은 stock 을 인자로 받지 않는다 —
 * 변경 경로는 반드시 {@link #adjustStock(Long, int)}.
 *
 * <p>응답 시 LazyInitializationException 방지: open-in-view=false 환경에서 컨트롤러는 닫힌
 * 세션의 엔티티를 직렬화한다. 모든 반환 엔티티는 {@link #initializeAssociations(Album)} 으로
 * artist/genre/label 프록시를 트랜잭션 내에서 강제 초기화한다 (admin 단건 한정 — 미세한 N+1).
 *
 * <p>공개 조회({@link #search}/{@link #findDetail}) 응답의 평점·리뷰 수는 {@link ReviewRepository} 의 집계 쿼리로
 * 채운다 — 목록은 페이지의 album id 묶음으로 1회, 단건은 해당 id 로 1회. album 본문 조회의 의도된 N+1(W10 시연)과 달리
 * 평점 집계는 N+1 을 만들지 않는다.
 */
@Service
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final GenreRepository genreRepository;
    private final LabelRepository labelRepository;
    private final ReviewRepository reviewRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;

    public AlbumService(AlbumRepository albumRepository,
                        ArtistRepository artistRepository,
                        GenreRepository genreRepository,
                        LabelRepository labelRepository,
                        ReviewRepository reviewRepository,
                        CartRepository cartRepository,
                        OrderRepository orderRepository) {
        this.albumRepository = albumRepository;
        this.artistRepository = artistRepository;
        this.genreRepository = genreRepository;
        this.labelRepository = labelRepository;
        this.reviewRepository = reviewRepository;
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
    }

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

    /**
     * 관리자 단건 재고조정 (delta 는 음수 가능). album 행을 비관락(SELECT … FOR UPDATE, #205 재사용)으로 잠가
     * 동시 조정·주문 차감과의 lost-update 를 막는다 (#234). 음수/오버플로 가드는 {@code Album.adjustStock} 에
     * 일원화되고, in-memory 엔티티가 권위값이라 반환 stock 이 신선하다 — 복원 핫경로의 원자적 UPDATE 와 달리
     * 갱신값을 그대로 응답해야 하므로 FOR-UPDATE + dirty-check 가 적합하다.
     */
    @Transactional
    public Album adjustStock(Long id, int delta) {
        Album album = albumRepository.findByIdForUpdate(id)
                .orElseThrow(AlbumNotFoundException::new);
        album.adjustStock(delta);
        return initializeAssociations(album);
    }

    /**
     * 앨범 삭제. 이 메서드가 트랜잭션 경계 소유자여야 한다 — 외부 {@code @Transactional} 안에서
     * 호출하면 {@code flush()} 실패 시 바깥 트랜잭션이 rollback-only 로 물들어 경계에서
     * {@code UnexpectedRollbackException} 이 발생하므로, 비트랜잭션 컨트롤러에서 직접 호출하는
     * 현재 구조를 유지한다.
     */
    @Transactional
    public void delete(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(AlbumNotFoundException::new);
        // cart_item/order_item 은 ON DELETE RESTRICT(V7/V8) 라 참조 중이면 409 로 거절한다.
        // review 는 ON DELETE CASCADE(V13) 라 사전검사 대상이 아니며 앨범과 함께 삭제된다.
        if (cartRepository.existsByAlbumId(id) || orderRepository.existsByAlbumId(id)) {
            throw new AlbumInUseException();
        }
        try {
            albumRepository.delete(album);
            albumRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new AlbumInUseException(ex);
        }
    }

    /**
     * 공개 검색/목록 (#34, API §3.3 GET /albums).
     *
     * <p>{@link AlbumSpecs} 의 동적 Specification 을 합쳐 페이징 조회한다. 응답 DTO 변환을
     * 트랜잭션 내에서 수행하여 {@code open-in-view=false} 환경에서도 LazyInitializationException
     * 을 피한다. artist/genre/label({@code @ManyToOne(LAZY)})은 {@link AlbumRepository#findAll}
     * 의 {@code @EntityGraph} 로 동반 페치하므로(#203) DTO 변환 시점의 N+1 SELECT 가 제거된다 —
     * 본 쿼리 1 + 평점집계 1 로 행수 무관 상수화 (이전 W9 베이스라인은 {@code 1 + 1 + 3P}).
     */
    @Transactional(readOnly = true)
    public Page<AlbumSummaryResponse> search(AlbumSearchCondition condition, Pageable pageable) {
        Specification<Album> spec = Specification
                .allOf(
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
        Page<Album> page = albumRepository.findAll(spec, pageable);
        Map<Long, AlbumRating> ratings = ratingsByAlbumId(page.getContent().stream().map(Album::getId).toList());
        return page.map(album -> AlbumSummaryResponse.from(album, ratings.getOrDefault(album.getId(), AlbumRating.NONE)));
    }

    /**
     * 공개 상세 (#34, API §3.3 GET /albums/{id}).
     *
     * <p>단건 조회는 LazyInitializationException 방지를 위해 연관을 명시 초기화한 뒤 DTO 변환한다.
     * Public 노출 정책상 status=HIDDEN 은 컨트롤러에서 거르지 않고 (관리자가 단건 URL 을 직접 줄 수
     * 있어야 함) 조회 자체는 status 무관하게 허용한다 — Public 목록(검색) 에서만 SELLING 으로 필터된다.
     */
    @Transactional(readOnly = true)
    public AlbumDetailResponse findDetail(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(AlbumNotFoundException::new);
        AlbumRating rating = ratingsByAlbumId(List.of(id)).getOrDefault(id, AlbumRating.NONE);
        return AlbumDetailResponse.from(initializeAssociations(album), rating);
    }

    /**
     * 앨범 묶음의 리뷰 집계를 1회 쿼리로 가져와 {@code albumId → }{@link AlbumRating} 맵으로 만든다.
     * 리뷰가 없는 앨범은 결과에 없으므로 호출 측에서 {@link AlbumRating#NONE} 으로 채운다. 빈 id 묶음이면 빈 맵 (빈 IN 절 회피).
     */
    private Map<Long, AlbumRating> ratingsByAlbumId(Collection<Long> albumIds) {
        if (albumIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.findRatingsByAlbumIds(albumIds).stream()
                .collect(Collectors.toMap(AlbumRatingView::getAlbumId, AlbumRating::from));
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

    /**
     * artist/genre/label lazy 프록시를 트랜잭션 내에서 강제 초기화. {@code create} 처럼 연관 엔티티가
     * 같은 세션에서 막 로드된 실엔티티인 경우에도, 일관성을 위해 항상 호출한다 — 미래에 새 lazy
     * 연관이 추가되어도 단일 진입점에서 처리되도록.
     */
    private Album initializeAssociations(Album album) {
        album.getArtist().getName();
        album.getGenre().getName();
        if (album.getLabel() != null) {
            album.getLabel().getName();
        }
        return album;
    }
}
