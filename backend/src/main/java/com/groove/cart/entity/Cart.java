package com.groove.cart.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import com.groove.global.common.BaseTimeEntity;
import com.groove.member.entity.Member;

import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원의 장바구니. 회원당 1개만 존재하며, 담긴 상품은 {@code CartItemRepository} 로 다룬다(컬렉션 매핑 없음). */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "cart", uniqueConstraints = @UniqueConstraint(name = "uk_cart_member", columnNames = "member_id"))
public class Cart extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_member"))
	private Member member;

	@Builder(access = PRIVATE)
	private Cart(Member member) {
		this.member = member;
	}

	public static Cart create(Member member) {
		return Cart.builder()
				.member(member)
				.build();
	}
}
