package com.groove.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.notification.entity.Notification;
import com.groove.notification.entity.NotificationType;
import com.groove.notification.repository.AlbumWatchRepository;
import com.groove.notification.repository.NotificationRepository;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.wishlist.repository.WishlistRepository;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

	private static final Long PRODUCT_ID = 100L;
	private static final Long ALBUM_ID = 200L;
	private static final String PRODUCT_TITLE = "Kind of Blue";
	private static final String ALBUM_TITLE = "A Love Supreme";

	@Mock
	WishlistRepository wishlistRepository;

	@Mock
	AlbumWatchRepository albumWatchRepository;

	@Mock
	NotificationRepository notificationRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	ProductRepository productRepository;

	@Mock
	AlbumRepository albumRepository;

	NotificationDispatcher notificationDispatcher;

	Product product;
	Album album;

	@BeforeEach
	void setUp() {
		notificationDispatcher = new NotificationDispatcher(wishlistRepository, albumWatchRepository,
				notificationRepository, memberRepository, productRepository, albumRepository);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
		album = AlbumFixture.withId(AlbumFixture.create(artist), ALBUM_ID);
	}

	private Member memberWithId(Long id) {
		return MemberFixture.withId(MemberFixture.create("member" + id + "@groove.com"), id);
	}

	@SuppressWarnings("unchecked")
	private ArgumentCaptor<List<Notification>> notificationListCaptor() {
		return ArgumentCaptor.forClass(List.class);
	}

	@Nested
	@DisplayName("dispatchRestock()")
	class DispatchRestock {

		@Test
		@DisplayName("알림 활성화한 위시리스트 구독자가 없으면 저장하지 않는다")
		void doesNotSaveWhenNoSubscribers() {
			// given
			given(wishlistRepository.findAlertEnabledMemberIdsByProductId(PRODUCT_ID)).willReturn(List.of());

			// when
			notificationDispatcher.dispatchRestock(new RestockEvent(PRODUCT_ID, PRODUCT_TITLE));

			// then
			verify(notificationRepository, never()).saveAll(any());
		}

		@Test
		@DisplayName("구독자 전원이 이미 안 읽은 알림을 가지고 있으면 저장하지 않는다")
		void doesNotSaveWhenAllSubscribersAlreadyNotified() {
			// given
			given(wishlistRepository.findAlertEnabledMemberIdsByProductId(PRODUCT_ID)).willReturn(List.of(1L, 2L));
			given(notificationRepository.findMemberIdsWithUnreadNotification(List.of(1L, 2L), PRODUCT_ID,
					NotificationType.RESTOCK)).willReturn(List.of(1L, 2L));

			// when
			notificationDispatcher.dispatchRestock(new RestockEvent(PRODUCT_ID, PRODUCT_TITLE));

			// then
			verify(notificationRepository, never()).saveAll(any());
		}

		@Test
		@DisplayName("일부만 이미 안 읽은 알림을 가지고 있으면 나머지 회원에게만 차집합으로 알림을 적재한다")
		void savesOnlyMembersWithoutUnreadNotification() {
			// given
			given(wishlistRepository.findAlertEnabledMemberIdsByProductId(PRODUCT_ID))
					.willReturn(List.of(1L, 2L, 3L));
			given(notificationRepository.findMemberIdsWithUnreadNotification(List.of(1L, 2L, 3L), PRODUCT_ID,
					NotificationType.RESTOCK)).willReturn(List.of(2L));
			given(productRepository.getReferenceById(PRODUCT_ID)).willReturn(product);
			given(memberRepository.getReferenceById(1L)).willReturn(memberWithId(1L));
			given(memberRepository.getReferenceById(3L)).willReturn(memberWithId(3L));

			// when
			notificationDispatcher.dispatchRestock(new RestockEvent(PRODUCT_ID, PRODUCT_TITLE));

			// then
			ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
			verify(notificationRepository).saveAll(captor.capture());
			assertThat(captor.getValue()).extracting(n -> n.getMember().getId())
					.containsExactlyInAnyOrder(1L, 3L);
		}

		@Test
		@DisplayName("titleSnapshot 과 type 은 이벤트 값 그대로 저장한다")
		void savesEventTitleAsSnapshot() {
			// given
			given(wishlistRepository.findAlertEnabledMemberIdsByProductId(PRODUCT_ID)).willReturn(List.of(1L));
			given(notificationRepository.findMemberIdsWithUnreadNotification(List.of(1L), PRODUCT_ID,
					NotificationType.RESTOCK)).willReturn(List.of());
			given(productRepository.getReferenceById(PRODUCT_ID)).willReturn(product);
			given(memberRepository.getReferenceById(1L)).willReturn(memberWithId(1L));

			// when
			notificationDispatcher.dispatchRestock(new RestockEvent(PRODUCT_ID, PRODUCT_TITLE));

			// then
			ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
			verify(notificationRepository).saveAll(captor.capture());
			assertThat(captor.getValue()).extracting(Notification::getTitleSnapshot).containsExactly(PRODUCT_TITLE);
			assertThat(captor.getValue()).extracting(Notification::getType)
					.containsExactly(NotificationType.RESTOCK);
		}
	}

	@Nested
	@DisplayName("dispatchPriceDrop()")
	class DispatchPriceDrop {

		@Test
		@DisplayName("알림 활성화한 위시리스트 구독자가 없으면 저장하지 않는다")
		void doesNotSaveWhenNoSubscribers() {
			// given
			given(wishlistRepository.findAlertEnabledMemberIdsByProductId(PRODUCT_ID)).willReturn(List.of());

			// when
			notificationDispatcher.dispatchPriceDrop(new PriceDropEvent(PRODUCT_ID, PRODUCT_TITLE));

			// then
			verify(notificationRepository, never()).saveAll(any());
		}

		@Test
		@DisplayName("일부만 이미 안 읽은 알림을 가지고 있으면 나머지 회원에게만 차집합으로 알림을 적재한다")
		void savesOnlyMembersWithoutUnreadNotification() {
			// given
			given(wishlistRepository.findAlertEnabledMemberIdsByProductId(PRODUCT_ID)).willReturn(List.of(1L, 2L));
			given(notificationRepository.findMemberIdsWithUnreadNotification(List.of(1L, 2L), PRODUCT_ID,
					NotificationType.PRICE_DROP)).willReturn(List.of(1L));
			given(productRepository.getReferenceById(PRODUCT_ID)).willReturn(product);
			given(memberRepository.getReferenceById(2L)).willReturn(memberWithId(2L));

			// when
			notificationDispatcher.dispatchPriceDrop(new PriceDropEvent(PRODUCT_ID, PRODUCT_TITLE));

			// then
			ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
			verify(notificationRepository).saveAll(captor.capture());
			assertThat(captor.getValue()).extracting(n -> n.getMember().getId()).containsExactly(2L);
			assertThat(captor.getValue()).extracting(Notification::getType)
					.containsExactly(NotificationType.PRICE_DROP);
		}
	}

	@Nested
	@DisplayName("dispatchNewPressing()")
	class DispatchNewPressing {

		@Test
		@DisplayName("앨범 구독자가 없으면 저장하지 않는다")
		void doesNotSaveWhenNoSubscribers() {
			// given
			given(albumWatchRepository.findMemberIdsByAlbumId(ALBUM_ID)).willReturn(List.of());

			// when
			notificationDispatcher.dispatchNewPressing(new NewPressingEvent(ALBUM_ID, ALBUM_TITLE));

			// then
			verify(notificationRepository, never()).saveAll(any());
		}

		@Test
		@DisplayName("구독자 전원이 이미 안 읽은 알림을 가지고 있으면 저장하지 않는다")
		void doesNotSaveWhenAllSubscribersAlreadyNotified() {
			// given
			given(albumWatchRepository.findMemberIdsByAlbumId(ALBUM_ID)).willReturn(List.of(1L));
			given(notificationRepository.findMemberIdsWithUnreadAlbumNotification(List.of(1L), ALBUM_ID,
					NotificationType.NEW_PRESSING)).willReturn(List.of(1L));

			// when
			notificationDispatcher.dispatchNewPressing(new NewPressingEvent(ALBUM_ID, ALBUM_TITLE));

			// then
			verify(notificationRepository, never()).saveAll(any());
		}

		@Test
		@DisplayName("일부만 이미 안 읽은 알림을 가지고 있으면 나머지 회원에게만 titleSnapshot 그대로 알림을 적재한다")
		void savesOnlyMembersWithoutUnreadNotification() {
			// given
			given(albumWatchRepository.findMemberIdsByAlbumId(ALBUM_ID)).willReturn(List.of(1L, 2L));
			given(notificationRepository.findMemberIdsWithUnreadAlbumNotification(List.of(1L, 2L), ALBUM_ID,
					NotificationType.NEW_PRESSING)).willReturn(List.of(1L));
			given(albumRepository.getReferenceById(ALBUM_ID)).willReturn(album);
			given(memberRepository.getReferenceById(2L)).willReturn(memberWithId(2L));

			// when
			notificationDispatcher.dispatchNewPressing(new NewPressingEvent(ALBUM_ID, ALBUM_TITLE));

			// then
			ArgumentCaptor<List<Notification>> captor = notificationListCaptor();
			verify(notificationRepository).saveAll(captor.capture());
			assertThat(captor.getValue()).extracting(n -> n.getMember().getId()).containsExactly(2L);
			assertThat(captor.getValue()).extracting(Notification::getTitleSnapshot).containsExactly(ALBUM_TITLE);
			assertThat(captor.getValue()).extracting(Notification::getType)
					.containsExactly(NotificationType.NEW_PRESSING);
		}
	}
}
