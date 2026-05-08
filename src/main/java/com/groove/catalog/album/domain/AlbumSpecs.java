package com.groove.catalog.album.domain;

import com.groove.catalog.artist.domain.Artist;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * 앨범 공개 검색용 {@link Specification} 모음 (#34, API §3.3 GET /albums).
 *
 * <p>각 메서드는 단일 필터를 표현하며 입력이 null/blank 일 때 {@code conjunction()}(noop)
 * 을 반환해 동적 조합 시 영향이 없도록 한다.
 *
 * <p><b>의도적 N+1 (W10 시연 보존)</b>: artist/genre/label 어떤 연관도
 * {@code root.fetch(...)} 로 끌어오지 않는다. 페치 조인을 추가하면 시연 자료가 사라지므로
 * 절대 추가 금지. {@link #keyword(String)} 는 artist 와의 LEFT JOIN 만 걸어 search 조건을
 * 만들고 fetch 는 하지 않는다 (Hibernate 가 별도 SELECT 로 artist/genre/label 을 N+1 로
 * 조회하는 경로 보존). ERD §4.6 [W10] 인덱스 누락도 함께 시연 자료로 보존.
 */
public final class AlbumSpecs {

    private AlbumSpecs() {
    }

    public static Specification<Album> hasArtistId(Long artistId) {
        if (artistId == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("artist").get("id"), artistId);
    }

    public static Specification<Album> hasGenreId(Long genreId) {
        if (genreId == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("genre").get("id"), genreId);
    }

    public static Specification<Album> hasFormat(AlbumFormat format) {
        if (format == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("format"), format);
    }

    public static Specification<Album> hasStatus(AlbumStatus status) {
        if (status == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Album> isLimited(Boolean limited) {
        if (limited == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("limited"), limited);
    }

    /**
     * 가격 범위 (inclusive). min/max 어느 쪽이 null 이어도 단방향만 적용한다.
     * 둘 다 null 이면 noop.
     */
    public static Specification<Album> priceBetween(Long minPrice, Long maxPrice) {
        if (minPrice == null && maxPrice == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> {
            if (minPrice != null && maxPrice != null) {
                return cb.between(root.get("price"), minPrice, maxPrice);
            }
            if (minPrice != null) {
                return cb.greaterThanOrEqualTo(root.get("price"), minPrice);
            }
            return cb.lessThanOrEqualTo(root.get("price"), maxPrice);
        };
    }

    /**
     * 발매 연도 범위 (inclusive). releaseYear 는 short 이지만 비교 인자는 int 로 받는다 —
     * 외부 입력이 short 한도를 넘는 경우 컨트롤러 검증 단계에서 거른다.
     */
    public static Specification<Album> yearBetween(Integer minYear, Integer maxYear) {
        if (minYear == null && maxYear == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> {
            if (minYear != null && maxYear != null) {
                return cb.between(root.get("releaseYear"), minYear.shortValue(), maxYear.shortValue());
            }
            if (minYear != null) {
                return cb.greaterThanOrEqualTo(root.get("releaseYear"), minYear.shortValue());
            }
            return cb.lessThanOrEqualTo(root.get("releaseYear"), maxYear.shortValue());
        };
    }

    /**
     * 키워드 OR 검색: {@code album.title LIKE %k%} OR {@code artist.name LIKE %k%} (대소문자 무시).
     *
     * <p>artist 는 search 조건 LEFT JOIN 만 사용하고 fetch 는 하지 않는다 — N+1 보존.
     * countQuery 가 join 을 중복 평가하면 결과 카운트가 부풀려질 수 있어, count 단계에서는
     * artist join 자체를 생성하지 않도록 query 가 count 인지 확인 후 skip 하지는 않는다 —
     * Spring Data 가 자동으로 페이징 count 쿼리를 별도로 빌드하므로 same Specification 재호출이
     * 안전하다 (artist 와 album 은 ManyToOne, count distinct 불필요).
     */
    public static Specification<Album> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            Join<Album, Artist> artist = root.join("artist", JoinType.LEFT);
            Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
            Predicate artistMatch = cb.like(cb.lower(artist.get("name")), pattern);
            return cb.or(titleMatch, artistMatch);
        };
    }
}
