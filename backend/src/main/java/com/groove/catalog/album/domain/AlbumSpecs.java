package com.groove.catalog.album.domain;

import com.groove.common.persistence.FulltextFunctionContributor;
import org.springframework.data.jpa.domain.Specification;

/**
 * 앨범 공개 검색용 Specification 모음. 각 메서드는 단일 필터이며 입력이 null/blank 면 unrestricted(noop)을 반환한다.
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

    /**
     * label 필터. root.get("label") 의 암묵 inner join 으로 label 미지정 앨범은 제외된다.
     */
    public static Specification<Album> hasLabelId(Long labelId) {
        if (labelId == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("label").get("id"), labelId);
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
     * 가격 범위 (inclusive). 한쪽만 null 이면 단방향만 적용, 둘 다 null 이면 noop.
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
     * 발매 연도 범위 (inclusive). releaseYear 는 short 이지만 비교 인자는 int 로 받는다.
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
     * 키워드 검색: 단일 테이블 FULLTEXT(title, artist_name)를 MATCH(...) AGAINST(? IN BOOLEAN MODE)로 검색한다.
     * relevance score > 0 인 행을 매칭으로 본다. 1글자 키워드는 ngram 최소 토큰(2) 미만이라 매칭되지 않는다.
     */
    public static Specification<Album> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Specification.unrestricted();
        }
        String phrase = toFulltextPhrase(keyword);
        if (phrase.isEmpty()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.greaterThan(
                cb.function(FulltextFunctionContributor.FUNCTION_NAME, Double.class,
                        root.get("title"), root.get("artistName"), cb.literal(phrase)),
                0.0);
    }

    /**
     * FULLTEXT boolean-mode 연산자/구분자를 제거한 뒤 따옴표 구문(phrase)으로 감싼다. 연산자만으로 이뤄진 입력은 빈 문자열을 반환한다.
     */
    private static String toFulltextPhrase(String keyword) {
        String sanitized = keyword.trim().replaceAll("[+\\-><()~*\"@]", " ").trim();
        if (sanitized.isBlank()) {
            return "";
        }
        return "\"" + sanitized + "\"";
    }
}
