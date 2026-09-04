package com.groove.limited.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import org.hibernate.annotations.ColumnDefault;

import com.groove.global.common.BaseTimeEntity;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;

import jakarta.persistence.Column;
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

/** 한정반 구매 이력. 1인 1회 방어(uk_limited_purchase)의 최종 방어선이며, order 는 PENDING 주문 생성 전에는 비어 있을 수 있다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "limited_purchase",
		uniqueConstraints = @UniqueConstraint(name = "uk_limited_purchase", columnNames = {"drop_id", "member_id"}))
public class LimitedPurchase extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "drop_id", nullable = false, foreignKey = @ForeignKey(name = "fk_limited_purchase_drop"))
	private LimitedDrop drop;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_limited_purchase_member"))
	private Member member;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "order_id", foreignKey = @ForeignKey(name = "fk_limited_purchase_order"))
	private Order order;

	@Column(nullable = false)
	@ColumnDefault("1")
	private int quantity;

	@Builder(access = PRIVATE)
	private LimitedPurchase(LimitedDrop drop, Member member, Order order, int quantity) {
		this.drop = drop;
		this.member = member;
		this.order = order;
		this.quantity = quantity;
	}

	public static LimitedPurchase create(LimitedDrop drop, Member member, Order order, int quantity) {
		if (quantity < 1 || quantity > drop.getPerMemberLimit()) {
			throw new BusinessException(ErrorCode.LIMITED_LIMIT_EXCEEDED);
		}

		return LimitedPurchase.builder()
				.drop(drop)
				.member(member)
				.order(order)
				.quantity(quantity)
				.build();
	}
}
