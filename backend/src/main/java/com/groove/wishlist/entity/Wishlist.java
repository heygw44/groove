package com.groove.wishlist.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import com.groove.global.common.BaseTimeEntity;
import com.groove.member.entity.Member;
import com.groove.product.entity.Product;

import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원이 관심 상품을 담아두는 위시리스트 한 줄. 회원-상품 조합당 행 하나만 존재한다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "wishlist",
		uniqueConstraints = @UniqueConstraint(name = "uk_wishlist_member_product",
				columnNames = {"member_id", "product_id"}))
public class Wishlist extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_wishlist_member"))
	private Member member;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_wishlist_product"))
	private Product product;

	@Builder(access = PRIVATE)
	private Wishlist(Member member, Product product) {
		this.member = member;
		this.product = product;
	}

	public static Wishlist create(Member member, Product product) {
		return Wishlist.builder()
				.member(member)
				.product(product)
				.build();
	}
}
