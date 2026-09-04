package com.groove.fixture;

import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedPurchase;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;

public final class LimitedPurchaseFixture {

	private static final int DEFAULT_QUANTITY = 1;

	private LimitedPurchaseFixture() {
	}

	public static LimitedPurchase create(LimitedDrop drop, Member member) {
		return LimitedPurchase.create(drop, member, null, DEFAULT_QUANTITY);
	}

	public static LimitedPurchase create(LimitedDrop drop, Member member, Order order, int quantity) {
		return LimitedPurchase.create(drop, member, order, quantity);
	}
}
