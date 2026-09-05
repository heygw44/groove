package com.groove.recommend.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품 상세 조회 로그. 비로그인 조회는 member 가 null 이다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "product_view_log",
		indexes = {
			@Index(name = "idx_view_log_member_viewed", columnList = "member_id, viewed_at"),
			@Index(name = "idx_view_log_product_viewed", columnList = "product_id, viewed_at")
		})
public class ProductViewLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", foreignKey = @ForeignKey(name = "fk_view_log_member"))
	private Member member;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_view_log_product"))
	private Product product;

	@Column(name = "viewed_at", nullable = false)
	private LocalDateTime viewedAt;

	@Builder(access = PRIVATE)
	private ProductViewLog(Member member, Product product, LocalDateTime viewedAt) {
		this.member = member;
		this.product = product;
		this.viewedAt = viewedAt;
	}

	public static ProductViewLog create(Member member, Product product, LocalDateTime viewedAt) {
		return ProductViewLog.builder()
				.member(member)
				.product(product)
				.viewedAt(viewedAt)
				.build();
	}
}
