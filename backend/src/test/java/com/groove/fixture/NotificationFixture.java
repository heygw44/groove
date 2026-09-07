package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.member.entity.Member;
import com.groove.notification.entity.Notification;
import com.groove.notification.entity.NotificationType;
import com.groove.product.entity.Album;
import com.groove.product.entity.Product;

public final class NotificationFixture {

	private static final String TITLE_SNAPSHOT = "Kind of Blue";

	private NotificationFixture() {
	}

	public static Notification forProduct(Member member, Product product) {
		return forProduct(member, product, NotificationType.RESTOCK);
	}

	public static Notification forProduct(Member member, Product product, NotificationType type) {
		return Notification.forProduct(member, product, type, TITLE_SNAPSHOT);
	}

	public static Notification forAlbum(Member member, Album album) {
		return Notification.forAlbum(member, album, TITLE_SNAPSHOT);
	}

	public static Notification withId(Notification notification, Long id) {
		ReflectionTestUtils.setField(notification, "id", id);
		return notification;
	}
}
