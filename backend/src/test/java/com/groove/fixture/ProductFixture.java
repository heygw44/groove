package com.groove.fixture;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.product.entity.Artist;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;

public final class ProductFixture {

	private static final BigDecimal PRICE = new BigDecimal("45000.00");
	private static final LocalDate RELEASE_DATE = LocalDate.of(2024, 1, 1);
	private static final String TITLE = "Kind of Blue";

	private ProductFixture() {
	}

	public static Product create(Artist artist) {
		return create(artist, null, TITLE);
	}

	public static Product create(Artist artist, Label label) {
		return create(artist, label, TITLE);
	}

	public static Product create(Artist artist, String title) {
		return create(artist, null, title);
	}

	private static Product create(Artist artist, Label label, String title) {
		return Product.create(title, artist, label, RELEASE_DATE, "180g", "Black", PRICE, "설명");
	}

	public static Product withId(Product product, Long id) {
		ReflectionTestUtils.setField(product, "id", id);
		return product;
	}
}
