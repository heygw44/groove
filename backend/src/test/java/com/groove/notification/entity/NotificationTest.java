package com.groove.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.NotificationFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

class NotificationTest {

	Member member = MemberFixture.create();
	Artist artist = ArtistFixture.create();

	@Nested
	@DisplayName("forProduct()")
	class ForProduct {

		@Test
		@DisplayName("product 알림을 생성하면 album 은 null 이다")
		void leavesAlbumNull() {
			// given
			Product product = ProductFixture.create(artist);

			// when
			Notification notification = NotificationFixture.forProduct(member, product, NotificationType.RESTOCK);

			// then
			assertThat(notification.getProduct()).isEqualTo(product);
			assertThat(notification.getAlbum()).isNull();
			assertThat(notification.getType()).isEqualTo(NotificationType.RESTOCK);
		}
	}

	@Nested
	@DisplayName("forAlbum()")
	class ForAlbum {

		@Test
		@DisplayName("album 알림을 생성하면 product 는 null 이고 type 은 NEW_PRESSING 이다")
		void leavesProductNull() {
			// given
			Album album = AlbumFixture.create(artist);

			// when
			Notification notification = NotificationFixture.forAlbum(member, album);

			// then
			assertThat(notification.getAlbum()).isEqualTo(album);
			assertThat(notification.getProduct()).isNull();
			assertThat(notification.getType()).isEqualTo(NotificationType.NEW_PRESSING);
		}
	}

	@Nested
	@DisplayName("markRead()")
	class MarkRead {

		@Test
		@DisplayName("읽지 않은 상태면 읽음 시각을 기록한다")
		void recordsReadAt() {
			// given
			Product product = ProductFixture.create(artist);
			Notification notification = NotificationFixture.forProduct(member, product);
			LocalDateTime now = LocalDateTime.of(2026, 9, 7, 10, 0);

			// when
			notification.markRead(now);

			// then
			assertThat(notification.getReadAt()).isEqualTo(now);
		}

		@Test
		@DisplayName("이미 읽은 상태면 다시 호출해도 최초 읽음 시각을 유지한다")
		void keepsFirstReadAt() {
			// given
			Product product = ProductFixture.create(artist);
			Notification notification = NotificationFixture.forProduct(member, product);
			LocalDateTime firstReadAt = LocalDateTime.of(2026, 9, 7, 10, 0);
			LocalDateTime secondReadAt = LocalDateTime.of(2026, 9, 7, 11, 0);
			notification.markRead(firstReadAt);

			// when
			notification.markRead(secondReadAt);

			// then
			assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
		}
	}
}
