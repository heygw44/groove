package com.groove.fixture;

import java.time.LocalDateTime;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.product.entity.Product;

public final class LimitedDropFixture {

	private static final int DEFAULT_TOTAL_QUANTITY = 100;
	private static final int DEFAULT_PER_MEMBER_LIMIT = 2;

	private LimitedDropFixture() {
	}

	public static LimitedDrop scheduled(Product product) {
		return scheduled(product, DEFAULT_TOTAL_QUANTITY, DEFAULT_PER_MEMBER_LIMIT);
	}

	public static LimitedDrop scheduled(Product product, int totalQuantity, Integer perMemberLimit) {
		return LimitedDrop.schedule(product, totalQuantity, perMemberLimit,
				LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
	}

	public static LimitedDrop open(Product product, int totalQuantity) {
		LimitedDrop drop = scheduled(product, totalQuantity, DEFAULT_PER_MEMBER_LIMIT);
		drop.open();
		return drop;
	}

	public static LimitedDrop withId(LimitedDrop drop, Long id) {
		ReflectionTestUtils.setField(drop, "id", id);
		return drop;
	}

	public static LimitedDrop withStatus(LimitedDrop drop, LimitedDropStatus status) {
		ReflectionTestUtils.setField(drop, "status", status);
		return drop;
	}

	public static LimitedDrop withSoldCount(LimitedDrop drop, int soldCount) {
		ReflectionTestUtils.setField(drop, "soldCount", soldCount);
		return drop;
	}

	public static LimitedDrop withOpenAt(LimitedDrop drop, LocalDateTime openAt) {
		ReflectionTestUtils.setField(drop, "openAt", openAt);
		return drop;
	}

	public static LimitedDrop withCloseAt(LimitedDrop drop, LocalDateTime closeAt) {
		ReflectionTestUtils.setField(drop, "closeAt", closeAt);
		return drop;
	}
}
