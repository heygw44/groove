package com.groove.review.domain;

import com.groove.catalog.album.domain.Album;
import com.groove.common.persistence.BaseTimeEntity;
import com.groove.member.domain.Member;
import com.groove.order.domain.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

/**
 * 상품 리뷰 (ERD §4.14, glossary §2.10).
 *
 * <p>배송 완료(DELIVERED 이상)된 본인 회원 주문의 주문 항목(album)에 대해 회원이 작성하는 1~5점 평가.
 * 1주문-1상품-1리뷰 — {@code uk_review_order_album} UNIQUE(order_id, album_id). 게스트 주문은 리뷰 불가
 * ({@code member_id} NOT NULL). 작성 가능 여부(본인/상태/항목 포함/중복)는 모두 {@code ReviewService} 가 검증한다 —
 * 도메인은 마지막 방어선인 {@code rating} 범위만 재검증한다 (DB CHECK 와 이중).
 *
 * <p>리뷰는 작성 후 수정하지 않는다(본 이슈 범위) — 모든 필드 불변, 변경 메서드 없음. 삭제는 본인만 가능하며
 * Repository 레벨에서 처리한다.
 */
@Entity
@Table(name = "review")
public class Review extends BaseTimeEntity {

    /** 평점 하한 — DB {@code ck_review_rating_range} 와 이중 방어선. */
    public static final int MIN_RATING = 1;
    /** 평점 상한 — DB {@code ck_review_rating_range} 와 이중 방어선. */
    public static final int MAX_RATING = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** DB 컬럼은 {@code TINYINT} (ERD §4.14) — 값 범위 1~5 라 1바이트면 충분하다. Hibernate 기본은 {@code int}→{@code INTEGER} 이므로 명시한다. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    protected Review() {
    }

    private Review(Member member, Album album, Order order, int rating, String content) {
        this.member = member;
        this.album = album;
        this.order = order;
        this.rating = rating;
        this.content = content;
    }

    /**
     * 리뷰 작성. {@code rating} 은 {@value #MIN_RATING}~{@value #MAX_RATING} 범위여야 한다 — 범위 밖이면
     * {@link IllegalArgumentException} (컨트롤러 {@code @Min/@Max} 가 1차로 막지만 도메인이 최종 방어선).
     * {@code content} 는 nullable — blank 문자열은 {@code null} 로 정규화한다.
     *
     * <p>호출 측({@code ReviewService}) 이 본인 주문 / 배송 완료 / 항목 포함 / 중복 없음 검증을 끝낸 상태로 전달한다고 가정한다.
     */
    public static Review write(Member member, Album album, Order order, int rating, String content) {
        Objects.requireNonNull(member, "member must not be null");
        Objects.requireNonNull(album, "album must not be null");
        Objects.requireNonNull(order, "order must not be null");
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException("rating must be between " + MIN_RATING + " and " + MAX_RATING + ": " + rating);
        }
        String normalizedContent = (content == null || content.isBlank()) ? null : content;
        return new Review(member, album, order, rating, normalizedContent);
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public Album getAlbum() {
        return album;
    }

    public Order getOrder() {
        return order;
    }

    public int getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }
}
