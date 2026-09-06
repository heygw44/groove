package com.groove.fixture;

import java.time.LocalDateTime;

import com.groove.member.entity.Member;
import com.groove.product.entity.Product;
import com.groove.recommend.entity.ProductViewLog;

public final class ProductViewLogFixture {

	private ProductViewLogFixture() {
	}

	public static ProductViewLog create(Member member, Product product, LocalDateTime viewedAt) {
		return ProductViewLog.create(member, product, viewedAt);
	}

	public static ProductViewLog createAnonymous(Product product, LocalDateTime viewedAt) {
		return ProductViewLog.create(null, product, viewedAt);
	}
}
