package com.groove.fixture;

import com.groove.product.entity.Artist;

public final class ArtistFixture {

	private ArtistFixture() {
	}

	public static Artist create() {
		return create("Miles Davis");
	}

	public static Artist create(String name) {
		return Artist.create(name, "영문명", "설명");
	}
}
