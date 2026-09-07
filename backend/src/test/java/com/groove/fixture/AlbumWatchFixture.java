package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.member.entity.Member;
import com.groove.notification.entity.AlbumWatch;
import com.groove.product.entity.Album;

public final class AlbumWatchFixture {

	private AlbumWatchFixture() {
	}

	public static AlbumWatch create(Member member, Album album) {
		return AlbumWatch.create(member, album);
	}

	public static AlbumWatch withId(AlbumWatch albumWatch, Long id) {
		ReflectionTestUtils.setField(albumWatch, "id", id);
		return albumWatch;
	}
}
