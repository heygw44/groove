package com.groove.fixture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.product.dto.ProductCreateRequest;
import com.groove.product.dto.ProductUpdateRequest;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;

public final class ProductFixture {

	private static final BigDecimal PRICE = new BigDecimal("45000");
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
		return Product.create(AlbumFixture.create(artist, title), title, artist, label, RELEASE_DATE, "180g",
				"Black", "US", RELEASE_DATE.getYear(), "CS 8163", "888880123456", EditionType.STANDARD, price,
				"설명");
	}

	public static Product create(Album album, Artist artist, Label label, String title, BigDecimal price) {
		return Product.create(album, title, artist, label, RELEASE_DATE, "180g", "Black", "US",
				RELEASE_DATE.getYear(), "CS 8163", "888880123456", EditionType.STANDARD, price, "설명");
	}

	private static Product create(Artist artist, Label label, String title) {
		return Product.create(AlbumFixture.create(artist, title), title, artist, label, RELEASE_DATE, "180g",
				"Black", "US", RELEASE_DATE.getYear(), "CS 8163", "888880123456", EditionType.STANDARD, PRICE,
				"설명");
	}

	public static Product withId(Product product, Long id) {
		ReflectionTestUtils.setField(product, "id", id);
		return product;
	}

	public static ProductCreateRequest createRequest(Long artistId, Long labelId, List<Long> genreIds) {
		return createRequest(artistId, labelId, genreIds, 1L, null);
	}

	public static ProductCreateRequest createRequest(Long artistId, Long labelId, List<Long> genreIds, Long albumId,
			ProductCreateRequest.NewAlbumRequest newAlbum) {
		return new ProductCreateRequest(TITLE, artistId, labelId, genreIds, RELEASE_DATE, "180g", "Black", PRICE,
				"설명", List.of("https://cdn.groove.com/0.jpg", "https://cdn.groove.com/1.jpg"), 10, albumId,
				newAlbum, "US", RELEASE_DATE.getYear(), "CS 8163", "888880123456", EditionType.STANDARD);
	}

	public static ProductUpdateRequest updateRequest(Long artistId, Long labelId, List<Long> genreIds) {
		JsonNullable<Long> label = labelId == null ? JsonNullable.undefined() : JsonNullable.of(labelId);
		return new ProductUpdateRequest("A Love Supreme", artistId, label, genreIds, RELEASE_DATE, "180g", "Black",
				PRICE, "수정된 설명", List.of("https://cdn.groove.com/updated.jpg"), JsonNullable.undefined(),
				JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), null);
	}

	public static ProductUpdateRequest emptyUpdateRequest() {
		return new ProductUpdateRequest(null, null, JsonNullable.undefined(), null, null, null, null, null, null,
				null, JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(),
				JsonNullable.undefined(), null);
	}

	public static ProductUpdateRequest updateRequestWithLabel(JsonNullable<Long> labelId) {
		return new ProductUpdateRequest(null, null, labelId, null, null, null, null, null, null, null,
				JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(),
				JsonNullable.undefined(), null);
	}
}
