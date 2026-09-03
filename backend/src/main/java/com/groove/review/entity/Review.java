package com.groove.review.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import com.groove.global.common.BaseTimeEntity;
import com.groove.member.entity.Member;
import com.groove.product.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 구매 확정 회원이 상품에 남기는 리뷰. 상품당 회원 1개로 제한한다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "review",
		uniqueConstraints = @UniqueConstraint(name = "uk_review_product_member",
				columnNames = {"product_id", "member_id"}),
		indexes = @Index(name = "idx_review_product", columnList = "product_id, created_at"))
public class Review extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_review_product"))
	private Product product;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_review_member"))
	private Member member;

	@Column(nullable = false)
	private int rating;

	@Column(length = 100)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String content;

	@Builder(access = PRIVATE)
	private Review(Product product, Member member, int rating, String title, String content) {
		this.product = product;
		this.member = member;
		this.rating = rating;
		this.title = title;
		this.content = content;
	}

	public static Review create(Product product, Member member, int rating, String title, String content) {
		return Review.builder()
				.product(product)
				.member(member)
				.rating(rating)
				.title(title)
				.content(content)
				.build();
	}

	public void update(int rating, String title, String content) {
		this.rating = rating;
		this.title = title;
		this.content = content;
	}

	public boolean isWrittenBy(Long memberId) {
		return this.member.getId().equals(memberId);
	}
}
