package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

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

	public static Artist withId(Long id) {
		return withId(create(), id);
	}

	public static Artist withId(Artist artist, Long id) {
		ReflectionTestUtils.setField(artist, "id", id);
		return artist;
	}
}
