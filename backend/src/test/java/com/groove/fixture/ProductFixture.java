package com.groove.fixture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.product.dto.ProductCreateRequest;
import com.groove.product.dto.ProductUpdateRequest;
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

	public static Product create(Artist artist, String title, BigDecimal price) {
		return create(artist, null, title, price);
	}

	public static Product create(Artist artist, Label label, String title, BigDecimal price) {
		return Product.create(title, artist, label, RELEASE_DATE, "180g", "Black", price, "설명");
	}

	private static Product create(Artist artist, Label label, String title) {
		return Product.create(title, artist, label, RELEASE_DATE, "180g", "Black", PRICE, "설명");
	}

	public static Product withId(Product product, Long id) {
		ReflectionTestUtils.setField(product, "id", id);
		return product;
	}

	public static ProductCreateRequest createRequest(Long artistId, Long labelId, List<Long> genreIds) {
		return new ProductCreateRequest(TITLE, artistId, labelId, genreIds, RELEASE_DATE, "180g", "Black", PRICE,
				"설명", List.of("https://cdn.groove.com/0.jpg", "https://cdn.groove.com/1.jpg"), 10);
	}

	public static ProductUpdateRequest updateRequest(Long artistId, Long labelId, List<Long> genreIds) {
		return new ProductUpdateRequest("A Love Supreme", artistId, labelId, genreIds, RELEASE_DATE, "180g", "Black",
				PRICE, "수정된 설명", List.of("https://cdn.groove.com/updated.jpg"));
	}

	public static ProductUpdateRequest emptyUpdateRequest() {
		return new ProductUpdateRequest(null, null, null, null, null, null, null, null, null, null);
	}
}
