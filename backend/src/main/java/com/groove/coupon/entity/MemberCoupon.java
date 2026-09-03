package com.groove.coupon.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;

import com.groove.global.common.BaseTimeEntity;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;

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

/** 회원에게 발급된 쿠폰 한 장. used_order_id 는 Order 쪽 member_coupon_id 와 마찬가지로 순환을 피해 FK 없이 둔다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "member_coupon",
		uniqueConstraints = @UniqueConstraint(name = "uk_member_coupon_member_coupon",
				columnNames = {"member_id", "coupon_id"}))
public class MemberCoupon extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_member_coupon_member"))
	private Member member;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "coupon_id", nullable = false, foreignKey = @ForeignKey(name = "fk_member_coupon_coupon"))
	private Coupon coupon;

	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean used;

	@Column(name = "used_order_id")
	private Long usedOrderId;

	@Column(name = "issued_at", nullable = false)
	private LocalDateTime issuedAt;

	@Column(name = "used_at")
	private LocalDateTime usedAt;

	@Builder(access = PRIVATE)
	private MemberCoupon(Member member, Coupon coupon) {
		this.member = member;
		this.coupon = coupon;
		this.used = false;
		this.issuedAt = LocalDateTime.now();
	}

	public static MemberCoupon issue(Member member, Coupon coupon) {
		return MemberCoupon.builder()
				.member(member)
				.coupon(coupon)
				.build();
	}

	public void use(Long orderId) {
		if (orderId == null) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		if (this.used) {
			throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
		}
		this.used = true;
		this.usedOrderId = orderId;
		this.usedAt = LocalDateTime.now();
	}

	public void restore() {
		if (!this.used) {
			throw new BusinessException(ErrorCode.COUPON_NOT_USED);
		}
		this.used = false;
		this.usedOrderId = null;
		this.usedAt = null;
	}

	public BigDecimal calculateDiscount(BigDecimal orderAmount) {
		if (this.used) {
			throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
		}
		return this.coupon.calculateDiscount(orderAmount);
	}
}
