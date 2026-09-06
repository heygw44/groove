package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;

public final class AlbumFixture {

	private static final String TITLE = "Kind of Blue";
	private static final int ORIGINAL_RELEASE_YEAR = 1959;

	private AlbumFixture() {
	}

	public static Album create(Artist artist) {
		return create(artist, TITLE);
	}

	public static Album create(Artist artist, String title) {
		return Album.create(title, artist, ORIGINAL_RELEASE_YEAR);
	}

	public static Album withId(Album album, Long id) {
		ReflectionTestUtils.setField(album, "id", id);
		return album;
	}
}
