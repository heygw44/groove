package com.groove.catalog.album.domain;

import com.groove.common.persistence.FulltextFunctionContributor;
import org.springframework.data.jpa.domain.Specification;

/**
 * 앨범 공개 검색용 {@link Specification} 모음 (#34, API §3.3 GET /albums).
 *
 * <p>각 메서드는 단일 필터를 표현하며 입력이 null/blank 일 때 {@code conjunction()}(noop)
 * 을 반환해 동적 조합 시 영향이 없도록 한다.
 *
 * <p>{@link #keyword(String)} 는 비정규화된 {@code artist_name} 을 포함한 단일 테이블
 * {@code FULLTEXT(title, artist_name)} 를 {@code MATCH ... AGAINST(... IN BOOLEAN MODE)} 로
 * 검색한다 (#204) — 풀스캔을 fulltext 인덱스 접근으로 전환한다. artist/genre/label 동반 페치는
 * {@code AlbumRepository} 의 {@code @EntityGraph}(#203) 가 담당하며 Specs 자체는 fetch 하지 않는다.
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
     * label 필터. label 은 nullable FK 라 {@code root.get("label")} 의 암묵 inner join 으로
     * label 미지정 앨범은 자연히 제외된다 — 특정 labelId 로 거를 때 의도된 동작.
     * genre 와 동일하게 fetch 는 하지 않아 의도된 N+1(W10 시연) 을 보존한다.
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
     * 키워드 검색: 비정규화 단일 테이블 {@code FULLTEXT(title, artist_name)} 를
     * {@code MATCH(title, artist_name) AGAINST(? IN BOOLEAN MODE)} 로 검색한다 (#204).
     * relevance score {@code > 0} 인 행을 매칭으로 본다.
     *
     * <p><b>왜 비정규화 + FULLTEXT 인가</b>: 기존 {@code LOWER(title) LIKE '%k%' OR LOWER(artist.
     * name) LIKE '%k%'} 는 선행 와일드카드 + {@code LOWER()} 래핑으로 인덱스를 못 타 50,000건
     * 풀스캔이었다(베이스라인 #196). title 과 artist.name 이 서로 다른 테이블이라 단순히 두
     * FULLTEXT 를 OR 로 묶으면 cross-table OR 가 인덱스를 무력화하므로, artist 이름을 album 에
     * 비정규화해 단일 FULLTEXT 한 방으로 구동한다 → {@code type=ALL → fulltext}.
     *
     * <p><b>의미 차이</b>: ngram(token=2) 파서 + 따옴표 구문(phrase) 으로 부분일치(substring)
     * 의미를 보존하지만, 1글자 키워드는 ngram 최소 토큰(2) 미만이라 매칭되지 않는다. 사용자 입력의
     * boolean-mode 연산자({@code + - > < ( ) ~ * " @}) 는 제거해 의도치 않은 FT 문법 해석을 막는다.
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
     * FULLTEXT boolean-mode 연산자/구분자를 제거한 뒤 따옴표 구문(phrase) 으로 감싼다 — ngram 파서가
     * 연속된 bigram(부분일치)으로 매칭하도록. 연산자만으로 이뤄진 입력은 빈 문자열을 반환해 호출 측이
     * noop 으로 처리한다. 닫는 따옴표가 구문을 깨지 않도록 입력의 {@code "} 도 함께 제거한다.
     */
    private static String toFulltextPhrase(String keyword) {
        String sanitized = keyword.trim().replaceAll("[+\\-><()~*\"@]", " ").trim();
        if (sanitized.isBlank()) {
            return "";
        }
        return "\"" + sanitized + "\"";
    }
}
