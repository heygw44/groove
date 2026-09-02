package com.groove.fixture;

import com.groove.product.entity.Genre;

public final class GenreFixture {

	private GenreFixture() {
	}

	public static Genre create(String name) {
		return Genre.create(name);
	}
}
