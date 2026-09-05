package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.member.entity.Member;

public final class MemberCouponFixture {

	private MemberCouponFixture() {
	}

	public static MemberCoupon create(Member member, Coupon coupon) {
		return MemberCoupon.issue(member, coupon);
	}

	public static MemberCoupon withId(MemberCoupon memberCoupon, Long id) {
		ReflectionTestUtils.setField(memberCoupon, "id", id);
		return memberCoupon;
	}

	public static MemberCoupon used(MemberCoupon memberCoupon, Long orderId) {
		memberCoupon.use(orderId);
		return memberCoupon;
	}
}
