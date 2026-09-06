package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.product.entity.Genre;

public final class GenreFixture {

	private GenreFixture() {
	}

	public static Genre create(String name) {
		return Genre.create(name);
	}

	public static Genre withId(Genre genre, Long id) {
		ReflectionTestUtils.setField(genre, "id", id);
		return genre;
	}
}
