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
 * 상품 리뷰. 배송 완료(DELIVERED 이상)된 본인 회원 주문 항목(album)에 대한 1~5점 평가.
 * 1주문-1상품-1리뷰 — uk_review_order_album UNIQUE(order_id, album_id). 게스트 주문은 리뷰 불가(member_id NOT NULL).
 * 작성 가능 여부 검증은 ReviewService 가 하고, 도메인은 rating 범위만 재검증한다. 모든 필드 불변, 변경 메서드 없음.
 */
@Entity
@Table(name = "review")
public class Review extends BaseTimeEntity {

    /** 평점 하한. */
    public static final int MIN_RATING = 1;
    /** 평점 상한. */
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

    /** DB 컬럼을 TINYINT 으로 매핑한다 (Hibernate 기본은 int→INTEGER). */
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
     * 리뷰 작성. rating 은 MIN_RATING~MAX_RATING 범위여야 한다 — 범위 밖이면 IllegalArgumentException.
     * content 는 nullable — blank 문자열은 null 로 정규화한다.
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
